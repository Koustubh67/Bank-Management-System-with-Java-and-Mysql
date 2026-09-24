package com.koustubh.bank.service;

import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.repository.LoanInstalmentRepository;
import com.koustubh.bank.repository.LoanRateChangeRepository;
import com.koustubh.bank.repository.LoanRepository;
import com.koustubh.bank.repository.RepoRateChangeRepository;
import com.koustubh.bank.repository.RepoRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Loan interest rates, fixed and floating.
 * <ul>
 *   <li>Floating rates are linked to the RBI repo rate, like the repo-linked (EBLR) loans Indian banks have offered
 *       since 2019: rate = repo rate + a spread that is fixed for the loan's life.</li>
 *   <li>Fixed rates cost a little more (see {@link LoanType#getFixedPremium()}) and never change.</li>
 *   <li>When staff record a repo rate change, every active floating-rate loan is repriced in the same transaction:
 *       the EMIs not yet paid and due after today are recalculated on the principal still owed, so the loan still
 *       ends on the same date. Paid and overdue EMIs keep their amounts. The customer is told the new EMI.</li>
 * </ul>
 */
@Service
public class LendingRateService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final BigDecimal MIN_REPO = new BigDecimal("0.50");
    private static final BigDecimal MAX_REPO = new BigDecimal("15.00");

    /** A loan product's rates today, for calculators and the application form. */
    public record Offer(LoanType type, BigDecimal floating, BigDecimal fixed) {

        public BigDecimal rate(RateType rateType) {
            return rateType == RateType.FIXED ? fixed : floating;
        }
    }

    public record RepoChange(RepoRateChange change, int repriced) {
    }

    private final RepoRateRepository repoRate;
    private final RepoRateChangeRepository repoHistory;
    private final LoanRepository loans;
    private final LoanInstalmentRepository instalments;
    private final LoanRateChangeRepository rateChanges;
    private final NotificationService notifications;
    private final Clock clock;

    public LendingRateService(RepoRateRepository repoRate, RepoRateChangeRepository repoHistory, LoanRepository loans,
                              LoanInstalmentRepository instalments, LoanRateChangeRepository rateChanges,
                              NotificationService notifications, Clock clock) {
        this.repoRate = repoRate;
        this.repoHistory = repoHistory;
        this.loans = loans;
        this.instalments = instalments;
        this.rateChanges = rateChanges;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- rates today

    @Transactional(readOnly = true)
    public RepoRate current() {
        return repoRate.findById(RepoRate.ID).orElseThrow();
    }

    /** Locks the repo rate until the caller's transaction ends. Loan approvals call this before anything else. */
    @Transactional(propagation = Propagation.MANDATORY)
    public RepoRate lockRepoRate() {
        return repoRate.lockCurrent().orElseThrow();
    }

    public static BigDecimal rate(LoanType type, RateType rateType, BigDecimal repo) {
        BigDecimal floating = repo.add(type.getSpread());
        return (rateType == RateType.FIXED ? floating.add(type.getFixedPremium()) : floating).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public BigDecimal rate(LoanType type, RateType rateType) {
        return rate(type, rateType, current().getRatePercent());
    }

    @Transactional(readOnly = true)
    public List<Offer> offers() {
        BigDecimal repo = current().getRatePercent();
        return Arrays.stream(LoanType.values())
                .map(t -> new Offer(t, rate(t, RateType.FLOATING, repo), rate(t, RateType.FIXED, repo))).toList();
    }

    @Transactional(readOnly = true)
    public List<RepoRateChange> history() {
        return repoHistory.findTop20ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public long activeFloatingLoans() {
        return loans.countByStatusAndRateType(LoanStatus.ACTIVE, RateType.FLOATING);
    }

    @Transactional(readOnly = true)
    public List<LoanRateChange> changesOf(Long loanId) {
        return rateChanges.findByLoanIdOrderByIdAsc(loanId);
    }

    // ---------------------------------------------------------------- repo rate change

    /**
     * Records a new repo rate and reprices every active floating-rate loan, all in one transaction. The repo rate row
     * is locked first (as in loan approval), then the loans in id order (as everywhere else), so this can't deadlock
     * with approvals or EMI collection.
     */
    @Transactional
    public RepoChange changeRepoRate(BigDecimal newRate, String note, String staff) {
        RepoRate repo = repoRate.lockCurrent().orElseThrow();
        List<String> errors = new ArrayList<>();
        if (newRate == null || newRate.compareTo(MIN_REPO) < 0 || newRate.compareTo(MAX_REPO) > 0) {
            errors.add("Repo rate must be between 0.50% and 15.00%");
        } else if (newRate.stripTrailingZeros().scale() > 2) {
            errors.add("Use at most 2 decimal places, e.g. 5.50");
        } else if (newRate.compareTo(repo.getRatePercent()) == 0) {
            errors.add("The repo rate is already " + repo.getRatePercent() + "%");
        }
        if (note == null || note.isBlank() || note.trim().length() > 200) {
            errors.add("Add a note (up to 200 characters), e.g. the RBI policy date");
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" · ", errors));
        }
        BigDecimal rate = newRate.setScale(2, RoundingMode.HALF_UP);
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        RepoRateChange change = repoHistory.save(new RepoRateChange(repo.getRatePercent(), rate, today, note.trim(), staff, now));
        repo.change(rate, today, staff, now);

        int repriced = 0;
        for (Loan loan : loans.findForUpdate(LoanStatus.ACTIVE, RateType.FLOATING)) {
            if (reprice(loan, change, today, now)) {
                repriced++;
            }
        }
        change.setLoansRepriced(repriced);
        return new RepoChange(change, repriced);
    }

    /** Recalculates the EMIs of one floating-rate loan that are unpaid and due after today. */
    private boolean reprice(Loan loan, RepoRateChange change, LocalDate today, LocalDateTime now) {
        BigDecimal newRate = change.getNewRate().add(loan.getSpreadPercent()).setScale(2, RoundingMode.HALF_UP);
        if (newRate.compareTo(loan.getRatePercent()) == 0) {
            return false;
        }
        List<LoanInstalment> schedule = instalments.findByLoanIdOrderByNumberAsc(loan.getId());
        int first = -1;
        for (int n = 0; n < schedule.size(); n++) {
            LoanInstalment i = schedule.get(n);
            if (!i.isPaid() && i.getDueDate().isAfter(today)) {
                first = n;
                break;
            }
        }
        if (first < 0) {
            return false; // every EMI is paid or already due: nothing left to reprice
        }
        // Paid and overdue EMIs come before the first future one, so the balance after the EMI just before it is
        // the principal the new rate applies to.
        BigDecimal balance = first == 0 ? loan.getPrincipal() : schedule.get(first - 1).getBalanceAfter();
        LoanInstalment from = schedule.get(first);
        List<EmiCalculator.Row> rows = EmiCalculator.schedule(balance, newRate, schedule.size() - first, from.getDueDate());
        for (int n = 0; n < rows.size(); n++) {
            EmiCalculator.Row r = rows.get(n);
            schedule.get(first + n).reschedule(r.emi(), r.principal(), r.interest(), r.balance());
        }
        BigDecimal oldRate = loan.getRatePercent();
        BigDecimal oldEmi = loan.getEmi();
        BigDecimal newEmi = rows.get(0).emi();
        loan.reprice(newRate, newEmi);
        rateChanges.save(new LoanRateChange(loan, from.getNumber(), from.getDueDate(), oldRate, newRate, oldEmi, newEmi,
                change.getNewRate(), change.getChangedBy(), now));
        notifications.notify(loan.getAccount().getCustomer(), "Interest rate " + (newRate.compareTo(oldRate) > 0 ? "increased" : "reduced"),
                "The RBI repo rate changed from " + change.getOldRate() + "% to " + change.getNewRate() + "%. Your floating-rate "
                        + loan.getType().getLabel().toLowerCase() + " " + loan.getReference() + " is now " + newRate
                        + "% p.a. (was " + oldRate + "%). From EMI " + from.getNumber() + " due " + from.getDueDate().format(DATE)
                        + " your EMI is Rs " + newEmi.toPlainString() + " (was Rs " + oldEmi.toPlainString()
                        + "). Your loan still ends on " + loan.getEndDate().format(DATE) + ".");
        return true;
    }
}
