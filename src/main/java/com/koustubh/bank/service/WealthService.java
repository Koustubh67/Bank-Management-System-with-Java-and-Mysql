package com.koustubh.bank.service;

import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.InsurancePolicyRepository;
import com.koustubh.bank.repository.InvestmentRepository;
import com.koustubh.bank.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Investments (fixed deposits, SIPs) and insurance bought from net banking. Every purchase debits the
 * customer's account through the same ledger as the ATM and UPI, inside one database transaction.
 * Rates and premiums are illustrative demo values.
 */
@Service
public class WealthService {

    public static final BigDecimal FD_MIN = BigDecimal.valueOf(5_000);
    public static final BigDecimal FD_MAX = BigDecimal.valueOf(1_000_000);
    public static final BigDecimal SIP_MIN = BigDecimal.valueOf(500);
    public static final BigDecimal SIP_MAX = BigDecimal.valueOf(100_000);

    public record Portfolio(List<Investment> investments, List<InsurancePolicy> policies, BigDecimal invested,
                            BigDecimal fdMaturityValue, BigDecimal lifeCover, BigDecimal healthCover) {

        public boolean isEmpty() {
            return investments.isEmpty() && policies.isEmpty();
        }
    }

    private final AccountRepository accounts;
    private final InvestmentRepository investments;
    private final InsurancePolicyRepository policies;
    private final TransactionRepository transactions;
    private final NumberGenerator numbers;
    private final Clock clock;

    public WealthService(AccountRepository accounts, InvestmentRepository investments, InsurancePolicyRepository policies,
                         TransactionRepository transactions, NumberGenerator numbers, Clock clock) {
        this.accounts = accounts;
        this.investments = investments;
        this.policies = policies;
        this.transactions = transactions;
        this.numbers = numbers;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Portfolio portfolio(String customerId) {
        Long accountId = account(customerId).getId();
        List<Investment> inv = investments.findByAccountIdOrderByIdDesc(accountId);
        List<InsurancePolicy> pol = policies.findByAccountIdOrderByIdDesc(accountId);
        BigDecimal invested = inv.stream().map(Investment::getInvested).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal maturity = inv.stream().filter(i -> i.getType() == InvestmentType.FIXED_DEPOSIT)
                .map(Investment::getMaturityAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal life = sumCover(pol, InsurancePlan.TERM_LIFE);
        BigDecimal health = sumCover(pol, InsurancePlan.HEALTH);
        return new Portfolio(inv, pol, invested, maturity, life, health);
    }

    /** Opens a fixed deposit and moves the money out of the savings balance. */
    @Transactional
    public Investment openFixedDeposit(String customerId, BigDecimal amount, FdTenure tenure) {
        Amounts.requirePositive(amount);
        if (tenure == null) {
            throw new InvalidRequestException("Please choose a tenure");
        }
        if (amount.compareTo(FD_MIN) < 0 || amount.compareTo(FD_MAX) > 0) {
            throw new InvalidRequestException("Fixed deposit amount must be between Rs 5,000 and Rs 10,00,000");
        }
        Account account = lockedAccount(customerId);
        String ref = uniqueInvestmentRef("JBFD");
        account.debit(amount);
        record(account, TransactionType.FD_BOOKING, amount, "FD/" + ref + "/" + tenure.getLabel() + " @ " + tenure.getRate() + "%");
        LocalDate today = LocalDate.now(clock);
        return investments.save(Investment.fixedDeposit(account, ref, amount, tenure, maturityAmount(amount, tenure),
                today, LocalDateTime.now(clock)));
    }

    /** Starts a monthly SIP; the first instalment is debited today. */
    @Transactional
    public Investment startSip(String customerId, Fund fund, BigDecimal monthly) {
        Amounts.requirePositive(monthly);
        if (fund == null) {
            throw new InvalidRequestException("Please choose a fund");
        }
        if (monthly.compareTo(SIP_MIN) < 0 || monthly.compareTo(SIP_MAX) > 0
                || monthly.remainder(BigDecimal.valueOf(100)).signum() != 0) {
            throw new InvalidRequestException("SIP amount must be a multiple of Rs 100 between Rs 500 and Rs 1,00,000");
        }
        Account account = lockedAccount(customerId);
        String ref = uniqueInvestmentRef("JBSIP");
        account.debit(monthly);
        record(account, TransactionType.SIP_INSTALMENT, monthly, "SIP/" + ref + "/" + fund.getLabel());
        return investments.save(Investment.sip(account, ref, fund, monthly, LocalDate.now(clock), LocalDateTime.now(clock)));
    }

    /** Buys a one-year policy; the annual premium is debited today. */
    @Transactional
    public InsurancePolicy buyPolicy(String customerId, InsurancePlan plan, Long cover, String details) {
        if (plan == null || cover == null || !plan.getCovers().contains(cover)) {
            throw new InvalidRequestException("Please choose a plan and a cover amount");
        }
        String cleanDetails = details == null ? "" : details.trim();
        if (plan.getDetailsLabel() != null && (cleanDetails.length() < 2 || cleanDetails.length() > 60)) {
            throw new InvalidRequestException("Please enter the " + plan.getDetailsLabel().toLowerCase());
        }
        Account account = lockedAccount(customerId);
        int age = age(account.getCustomer());
        if ((plan == InsurancePlan.TERM_LIFE || plan == InsurancePlan.HEALTH) && (age < 18 || age > 65)) {
            throw new InvalidRequestException(plan.getLabel() + " insurance is available from age 18 to 65");
        }
        BigDecimal premium = premium(plan, cover, age);
        String number;
        do {
            number = numbers.newReference("JBI");
        } while (policies.existsByPolicyNumber(number));
        account.debit(premium);
        record(account, TransactionType.INSURANCE_PREMIUM, premium, "INS/" + number + "/" + plan.getLabel());
        return policies.save(new InsurancePolicy(account, plan, number, BigDecimal.valueOf(cover), premium,
                plan.getDetailsLabel() == null ? null : cleanDetails, LocalDate.now(clock), LocalDateTime.now(clock)));
    }

    /** Premium quotes for every plan and cover option, for this customer's age. */
    @Transactional(readOnly = true)
    public Map<InsurancePlan, Map<Long, BigDecimal>> quotes(String customerId) {
        int age = age(account(customerId).getCustomer());
        Map<InsurancePlan, Map<Long, BigDecimal>> quotes = new EnumMap<>(InsurancePlan.class);
        for (InsurancePlan plan : InsurancePlan.values()) {
            Map<Long, BigDecimal> byCover = new LinkedHashMap<>();
            plan.getCovers().forEach(cover -> byCover.put(cover, premium(plan, cover, age)));
            quotes.put(plan, byCover);
        }
        return quotes;
    }

    /** Illustrative annual premium, rounded to whole rupees. */
    static BigDecimal premium(InsurancePlan plan, long cover, int age) {
        double value = switch (plan) {
            case TERM_LIFE -> cover / 100_000.0 * (60 + Math.max(0, age - 25) * 6);
            case HEALTH -> (cover <= 500_000 ? 6_500 : cover <= 1_000_000 ? 9_800 : 15_500)
                    * (1 + Math.max(0, age - 30) * 0.03);
            case MOTOR -> cover * 0.028;
            case TRAVEL -> cover <= 500_000 ? 899 : 1_499;
        };
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).setScale(2);
    }

    /** Interest compounded quarterly, like most Indian bank FDs: P × (1 + r/400)^(quarters). */
    static BigDecimal maturityAmount(BigDecimal principal, FdTenure tenure) {
        BigDecimal quarterly = BigDecimal.ONE.add(tenure.getRate().divide(BigDecimal.valueOf(400), MathContext.DECIMAL64));
        return principal.multiply(quarterly.pow(tenure.getMonths() / 3, MathContext.DECIMAL64))
                .setScale(2, RoundingMode.HALF_EVEN);
    }

    private int age(Customer customer) {
        return Period.between(customer.getDateOfBirth(), LocalDate.now(clock)).getYears();
    }

    private String uniqueInvestmentRef(String prefix) {
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
        Long id = account(customerId).getId();
        return accounts.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private static BigDecimal sumCover(List<InsurancePolicy> policies, InsurancePlan plan) {
        return policies.stream().filter(p -> p.getPlan() == plan).map(InsurancePolicy::getCover)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
