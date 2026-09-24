package com.koustubh.bank.service;

import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.market.FundCatalog;
import com.koustubh.bank.market.MarketDataService;
import com.koustubh.bank.market.MarketDataService.FundSnapshot;
import com.koustubh.bank.market.NavPoint;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.InvestmentRepository;
import com.koustubh.bank.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fixed deposits and real mutual funds (SIP and one-time). Mutual fund units are bought at the real published
 * NAV, so the portfolio's value and profit or loss move with the market. Every purchase debits the customer's
 * account through the same locked ledger as the ATM and UPI.
 */
@Service
public class InvestmentService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);

    public static final BigDecimal FD_MIN = BigDecimal.valueOf(5_000);
    public static final BigDecimal FD_MAX = BigDecimal.valueOf(1_000_000);
    public static final BigDecimal SIP_MIN = BigDecimal.valueOf(500);
    public static final BigDecimal LUMPSUM_MIN = BigDecimal.valueOf(1_000);
    public static final BigDecimal MF_MAX = BigDecimal.valueOf(500_000);

    /** What the customer is buying, kept in the session between the checkout steps. */
    public record Order(InvestmentType type, Long schemeCode, BigDecimal amount, FdTenure tenure) implements Serializable {
    }

    /** One holding valued at today's NAV (mutual funds) or with interest earned so far (FDs). */
    public record Holding(Investment investment, BigDecimal currentValue, BigDecimal gain, BigDecimal gainPercent,
                          NavPoint nav, boolean priced) {
    }

    public record Portfolio(List<Holding> holdings, BigDecimal invested, BigDecimal currentValue, BigDecimal gain,
                            BigDecimal gainPercent, boolean allPriced) {

        public boolean isEmpty() {
            return holdings.isEmpty();
        }
    }

    private final AccountRepository accounts;
    private final InvestmentRepository investments;
    private final TransactionRepository transactions;
    private final MarketDataService market;
    private final NumberGenerator numbers;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate tx;

    public InvestmentService(AccountRepository accounts, InvestmentRepository investments,
                             TransactionRepository transactions, MarketDataService market, NumberGenerator numbers,
                             NotificationService notifications, Clock clock, PlatformTransactionManager txManager) {
        this.accounts = accounts;
        this.investments = investments;
        this.transactions = transactions;
        this.market = market;
        this.numbers = numbers;
        this.notifications = notifications;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------------------------------------------------------------- orders and checkout

    /** Checks an order before checkout. Throws with a customer-friendly message. */
    public Order validate(Order order) {
        if (order.type() == null) {
            throw new InvalidRequestException("Please choose what to invest in");
        }
        BigDecimal amount = order.amount();
        Amounts.requirePositive(amount);
        switch (order.type()) {
            case FIXED_DEPOSIT -> {
                if (order.tenure() == null) {
                    throw new InvalidRequestException("Please choose a tenure");
                }
                if (amount.compareTo(FD_MIN) < 0 || amount.compareTo(FD_MAX) > 0) {
                    throw new InvalidRequestException("Fixed deposit amount must be between Rs 5,000 and Rs 10,00,000");
                }
            }
            case SIP, LUMPSUM -> {
                if (order.schemeCode() == null || FundCatalog.find(order.schemeCode()).isEmpty()) {
                    throw new InvalidRequestException("Please choose a fund");
                }
                BigDecimal min = order.type() == InvestmentType.SIP ? SIP_MIN : LUMPSUM_MIN;
                if (amount.compareTo(min) < 0 || amount.compareTo(MF_MAX) > 0
                        || amount.remainder(BigDecimal.valueOf(100)).signum() != 0) {
                    throw new InvalidRequestException((order.type() == InvestmentType.SIP ? "SIP" : "Investment")
                            + " amount must be a multiple of Rs 100 between Rs " + min.toPlainString()
                            + " and Rs 5,00,000");
                }
                market.snapshot(order.schemeCode()); // fails early if live prices are unavailable
            }
        }
        return order;
    }

    /** Pays for a validated order from the account. The payment method has already been verified (OTP or UPI PIN). */
    @Transactional
    public Investment pay(String customerId, Order order, PaymentMethod method) {
        validate(order);
        Account account = lockedAccount(customerId);
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        String via = method == PaymentMethod.UPI ? " via UPI" : "";
        Investment investment;
        if (order.type() == InvestmentType.FIXED_DEPOSIT) {
            String ref = uniqueRef("JBFD");
            account.debit(order.amount());
            record(account, TransactionType.FD_BOOKING, order.amount(),
                    "FD/" + ref + "/" + order.tenure().getLabel() + " @ " + order.tenure().getRate() + "%" + via);
            investment = investments.save(Investment.fixedDeposit(account, ref, order.amount(), order.tenure(),
                    maturityAmount(order.amount(), order.tenure()), today, method, now));
            notifications.notify(account.getCustomer(), "Fixed deposit opened",
                    "Your FD " + ref + " of Rs " + order.amount().toPlainString() + " is open and matures on "
                            + investment.getMaturityDate() + " at Rs " + investment.getMaturityAmount().toPlainString() + ".");
        } else {
            FundSnapshot fund = market.snapshot(order.schemeCode());
            BigDecimal units = units(order.amount(), fund.nav());
            String ref = uniqueRef(order.type() == InvestmentType.SIP ? "JBSIP" : "JBMF");
            account.debit(order.amount());
            record(account, order.type() == InvestmentType.SIP ? TransactionType.SIP_INSTALMENT : TransactionType.MF_PURCHASE,
                    order.amount(), (order.type() == InvestmentType.SIP ? "SIP/" : "MF/") + ref + "/"
                            + fund.listing().shortName() + via);
            investment = investments.save(Investment.mutualFund(account, order.type(), ref, order.schemeCode(),
                    fund.schemeName(), order.amount(), units, today, method, now));
            notifications.notify(account.getCustomer(), "Investment confirmed",
                    "Rs " + order.amount().toPlainString() + " invested in " + fund.listing().shortName() + ": "
                            + units.toPlainString() + " units at NAV Rs " + fund.nav().toPlainString() + " ("
                            + fund.navDate() + "). Ref " + ref + ".");
        }
        return investment;
    }

    // ---------------------------------------------------------------- portfolio

    @Transactional(readOnly = true)
    public Portfolio portfolio(String customerId) {
        return value(investments.findByAccountIdOrderByIdDesc(account(customerId).getId()));
    }

    @Transactional(readOnly = true)
    public List<Holding> allHoldings() {
        return value(investments.findAllByOrderByIdDesc()).holdings();
    }

    @Transactional(readOnly = true)
    public Holding holding(String customerId, Long investmentId) {
        Investment inv = investments.findById(investmentId)
                .filter(i -> i.getAccount().getCustomer().getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Investment not found"));
        return value(inv);
    }

    private Portfolio value(List<Investment> list) {
        List<Holding> holdings = new ArrayList<>();
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;
        boolean allPriced = true;
        for (Investment inv : list) {
            Holding h = value(inv);
            holdings.add(h);
            invested = invested.add(inv.getInvested());
            current = current.add(h.currentValue());
            allPriced &= h.priced();
        }
        BigDecimal gain = current.subtract(invested);
        return new Portfolio(holdings, invested, current, gain, percent(gain, invested), allPriced);
    }

    private Holding value(Investment inv) {
        if (inv.getType() == InvestmentType.FIXED_DEPOSIT) {
            BigDecimal value = accruedValue(inv, LocalDate.now(clock));
            BigDecimal gain = value.subtract(inv.getAmount());
            return new Holding(inv, value, gain, percent(gain, inv.getAmount()), null, true);
        }
        try {
            NavPoint nav = market.latest(inv.getSchemeCode());
            BigDecimal units = inv.getUnits() != null ? inv.getUnits() : unitsBoughtSince(inv);
            BigDecimal value = units.multiply(nav.nav()).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal gain = value.subtract(inv.getInvested());
            return new Holding(inv, value, gain, percent(gain, inv.getInvested()), nav, true);
        } catch (RuntimeException e) {
            // Prices unavailable: show the amount invested and say so, rather than guessing a value
            return new Holding(inv, inv.getInvested(), BigDecimal.ZERO, BigDecimal.ZERO, null, false);
        }
    }

    // ---------------------------------------------------------------- monthly SIP debits

    /** Runs every day at 9:30 in the bank's time zone; staff can also run it from the admin panel. */
    @Scheduled(cron = "0 30 9 * * *", zone = "${bank.zone}")
    public int processDueSips() {
        LocalDate today = LocalDate.now(clock);
        int processed = 0;
        for (Investment due : investments.findByTypeAndNextDebitDateLessThanEqual(InvestmentType.SIP, today)) {
            Integer done = tx.execute(status -> processSip(due.getId(), today));
            processed += done == null ? 0 : done;
        }
        return processed;
    }

    private int processSip(Long investmentId, LocalDate today) {
        Investment sip = investments.findById(investmentId).orElseThrow();
        Account account = accounts.findByIdForUpdate(sip.getAccount().getId()).orElseThrow();
        int paid = 0;
        while (!sip.getNextDebitDate().isAfter(today)) {
            LocalDate due = sip.getNextDebitDate();
            try {
                NavPoint nav = market.navOn(sip.getSchemeCode(), due);
                account.debit(sip.getAmount());
                record(account, TransactionType.SIP_INSTALMENT, sip.getAmount(),
                        "SIP/" + sip.getReference() + "/instalment " + (sip.getInstalmentsPaid() + 1));
                sip.addInstalment(units(sip.getAmount(), nav.nav()));
                paid++;
            } catch (RuntimeException e) {
                log.info("SIP {} instalment due {} skipped: {}", sip.getReference(), due, e.getMessage());
                notifications.notify(account.getCustomer(), "SIP instalment missed",
                        "Your SIP " + sip.getReference() + " instalment due " + due + " could not be paid: "
                                + e.getMessage() + ". We'll try again next month.");
                sip.skipInstalment();
                break;
            }
        }
        return paid;
    }

    // ---------------------------------------------------------------- demo data support

    /**
     * Demo data only: records a holding the customer already had before joining (an SIP running for
     * {@code instalments} months), priced with the real historical NAVs, without touching the account balance.
     */
    @Transactional
    public Investment importExistingSip(String customerId, long schemeCode, BigDecimal monthly, int instalments) {
        Account account = account(customerId);
        LocalDate start = LocalDate.now(clock).minusMonths(instalments - 1L);
        String name = market.history(schemeCode).schemeName();
        BigDecimal units = BigDecimal.ZERO;
        for (int m = 0; m < instalments; m++) {
            units = units.add(units(monthly, market.navOn(schemeCode, start.plusMonths(m)).nav()));
        }
        Investment sip = Investment.mutualFund(account, InvestmentType.SIP, uniqueRef("JBSIP"), schemeCode, name,
                monthly, BigDecimal.ZERO, start, PaymentMethod.ACCOUNT, LocalDateTime.now(clock));
        for (int m = 1; m < instalments; m++) {
            sip.addInstalment(BigDecimal.ZERO);
        }
        sip.setUnits(units);
        return investments.save(sip);
    }

    /** Works out units for SIPs created before units were tracked, from real NAVs. Safe to run repeatedly. */
    @Transactional
    public void backfillMissingUnits() {
        for (Investment inv : investments.findAll()) {
            if (inv.getType().isMutualFund() && inv.getUnits() == null) {
                try {
                    inv.setUnits(unitsBoughtSince(inv));
                } catch (RuntimeException e) {
                    log.info("Could not backfill units for {}: {}", inv.getReference(), e.getMessage());
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Tests only: save a modified investment (e.g. to move its next debit date into the past). */
    @Transactional
    void saveForTest(Investment investment) {
        investments.save(investment);
    }

    private BigDecimal unitsBoughtSince(Investment inv) {
        BigDecimal units = BigDecimal.ZERO;
        for (int m = 0; m < inv.getInstalmentsPaid(); m++) {
            units = units.add(units(inv.getAmount(), market.navOn(inv.getSchemeCode(), inv.getStartDate().plusMonths(m)).nav()));
        }
        return units;
    }

    static BigDecimal units(BigDecimal amount, BigDecimal nav) {
        return amount.divide(nav, 4, RoundingMode.DOWN);
    }

    /** Interest compounded quarterly, like most Indian bank FDs: P × (1 + r/400)^(quarters). */
    static BigDecimal maturityAmount(BigDecimal principal, FdTenure tenure) {
        BigDecimal quarterly = BigDecimal.ONE.add(tenure.getRate().divide(BigDecimal.valueOf(400), MathContext.DECIMAL64));
        return principal.multiply(quarterly.pow(tenure.getMonths() / 3, MathContext.DECIMAL64))
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    /** FD value today, with interest earned so far (continuous compounding at the quarterly rate). */
    static BigDecimal accruedValue(Investment fd, LocalDate today) {
        long days = Math.max(0, Math.min(ChronoUnit.DAYS.between(fd.getStartDate(), today),
                ChronoUnit.DAYS.between(fd.getStartDate(), fd.getMaturityDate())));
        double quarters = days / 91.25;
        double factor = Math.pow(1 + fd.getRatePercent().doubleValue() / 400, quarters);
        return fd.getAmount().multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private String uniqueRef(String prefix) {
        String ref;
        do {
            ref = numbers.newReference(prefix);
        } while (investments.existsByReference(ref));
        return ref;
    }

    private void record(Account account, TransactionType type, BigDecimal amount, String remarks) {
        transactions.save(new Transaction(account, type, amount, UUID.randomUUID().toString(), null, remarks,
                LocalDateTime.now(clock)));
    }

    private Account account(String customerId) {
        return accounts.findByCustomerLogin(customerId).orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private Account lockedAccount(String customerId) {
        return accounts.findByIdForUpdate(account(customerId).getId())
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
