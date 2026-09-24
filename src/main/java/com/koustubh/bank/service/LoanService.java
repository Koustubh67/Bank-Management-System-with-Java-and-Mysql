package com.koustubh.bank.service;

import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AccountRepository;
import com.koustubh.bank.repository.LoanEnquiryRepository;
import com.koustubh.bank.repository.LoanInstalmentRepository;
import com.koustubh.bank.repository.LoanRepository;
import com.koustubh.bank.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Loans from application to closure.
 * <ul>
 *   <li>Apply: validated (every problem reported at once) and checked for affordability (EMI ≤ 50% of income).</li>
 *   <li>Approve: in one transaction the loan and account are locked, the EMI schedule is generated, and the money is
 *       credited to the customer's account through the ledger. An application can be decided only once.</li>
 *   <li>Repay: a daily job debits every EMI that has fallen due, oldest first. If the balance is too low the EMI is
 *       marked overdue (the customer is alerted once) and retried the next day. Customers can also pay the next EMI
 *       early. The loan closes itself when the last EMI is paid.</li>
 * </ul>
 */
@Service
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public static final List<String> EMPLOYMENT = List.of("Salaried", "Self-employed professional", "Business owner",
            "Student", "Retired", "Other");
    public static final List<String> CALL_TIMES = List.of("Morning (9–12)", "Afternoon (12–4)", "Evening (4–8)");
    /** Banks usually cap all EMIs at about half of monthly income (FOIR). */
    public static final BigDecimal MAX_FOIR = BigDecimal.valueOf(50);

    public record Application(LoanType type, BigDecimal amount, Integer months, String purpose, String employment,
                              BigDecimal monthlyIncome) {
    }

    public record Enquiry(String name, String mobile, String email, String city, LoanType type, BigDecimal amount,
                          String employment, BigDecimal monthlyIncome, String preferredTime) {
    }

    /** EMI quote shown on calculators and to loan officers. {@code foir} is null if income is unknown. */
    public record Quote(BigDecimal emi, BigDecimal totalInterest, BigDecimal totalPayable, BigDecimal foir,
                        boolean affordable) {
    }

    /** A loan with its schedule, as the customer and staff see it. */
    public record LoanView(Loan loan, List<LoanInstalment> schedule, LoanInstalment next, int paid, int overdue,
                           BigDecimal totalInterest, BigDecimal interestPaid, Quote requestedQuote) {

        public int getProgressPercent() {
            return schedule.isEmpty() ? 0 : paid * 100 / schedule.size();
        }
    }

    private final LoanRepository loans;
    private final LoanInstalmentRepository instalments;
    private final LoanEnquiryRepository enquiries;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final NumberGenerator numbers;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate tx;

    public LoanService(LoanRepository loans, LoanInstalmentRepository instalments, LoanEnquiryRepository enquiries,
                       AccountRepository accounts, TransactionRepository transactions, NumberGenerator numbers,
                       NotificationService notifications, Clock clock, PlatformTransactionManager txManager) {
        this.loans = loans;
        this.instalments = instalments;
        this.enquiries = enquiries;
        this.accounts = accounts;
        this.transactions = transactions;
        this.numbers = numbers;
        this.notifications = notifications;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    // ---------------------------------------------------------------- quotes

    public Quote quote(BigDecimal amount, BigDecimal rate, int months, BigDecimal monthlyIncome) {
        List<EmiCalculator.Row> rows = EmiCalculator.schedule(amount, rate, months, LocalDate.now(clock));
        BigDecimal emi = rows.get(0).emi();
        BigDecimal interest = EmiCalculator.totalInterest(rows);
        BigDecimal foir = monthlyIncome == null || monthlyIncome.signum() <= 0 ? null
                : emi.multiply(BigDecimal.valueOf(100)).divide(monthlyIncome, 1, RoundingMode.HALF_UP);
        return new Quote(emi, interest, amount.add(interest), foir, foir == null || foir.compareTo(MAX_FOIR) <= 0);
    }

    // ---------------------------------------------------------------- customer: apply

    @Transactional
    public Loan apply(String customerId, Application a) {
        List<String> errors = validate(a.type(), a.amount(), a.months(), a.employment(), a.monthlyIncome());
        if (a.purpose() == null || a.purpose().isBlank() || a.purpose().trim().length() > 200) {
            errors.add("Tell us what the loan is for");
        }
        Account account = account(customerId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            errors.add("Your account must be active to apply for a loan");
        }
        if (a.type() != null && loans.existsByAccountIdAndTypeAndStatus(account.getId(), a.type(), LoanStatus.APPLIED)) {
            errors.add("You already have a " + a.type().getLabel().toLowerCase() + " application under review");
        }
        if (errors.isEmpty()) {
            Quote q = quote(a.amount(), a.type().getRate(), a.months(), a.monthlyIncome());
            if (!q.affordable()) {
                errors.add("The EMI of Rs " + q.emi().toPlainString() + " would be " + q.foir()
                        + "% of your monthly income. Banks allow up to 50%: try a smaller amount or a longer tenure");
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" · ", errors));
        }
        String ref;
        do {
            ref = numbers.newReference("JBL");
        } while (loans.existsByReference(ref));
        Loan loan = loans.save(new Loan(account, a.type(), ref, a.amount().setScale(2, RoundingMode.HALF_UP), a.months(),
                a.purpose().trim(), a.employment(), a.monthlyIncome().setScale(2, RoundingMode.HALF_UP), LocalDateTime.now(clock)));
        notifications.notify(account.getCustomer(), "Loan application received",
                "We've received your " + a.type().getLabel().toLowerCase() + " application " + ref + " for Rs "
                        + a.amount().toPlainString() + ". A loan officer will review it within 24–48 hours; we'll SMS and email you the decision.");
        return loan;
    }

    // ---------------------------------------------------------------- views

    @Transactional(readOnly = true)
    public List<LoanView> loansOf(String customerId) {
        return loans.findByAccountIdOrderByIdDesc(account(customerId).getId()).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public LoanView loanOf(String customerId, Long loanId) {
        Loan loan = loans.findWithCustomerById(loanId)
                .filter(l -> l.getAccount().getCustomer().getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Loan not found"));
        return view(loan);
    }

    @Transactional(readOnly = true)
    public LoanView loanForStaff(Long loanId) {
        return view(loans.findWithCustomerById(loanId).orElseThrow(() -> new NotFoundException("Loan not found")));
    }

    @Transactional(readOnly = true)
    public List<LoanView> byStatus(LoanStatus... statuses) {
        return loans.findByStatusInOrderByIdAsc(List.of(statuses)).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public long pendingApplications() {
        return loans.countByStatus(LoanStatus.APPLIED);
    }

    @Transactional(readOnly = true)
    public long overdueEmis() {
        return instalments.countByStatus(InstalmentStatus.OVERDUE);
    }

    private LoanView view(Loan loan) {
        List<LoanInstalment> schedule = instalments.findByLoanIdOrderByNumberAsc(loan.getId());
        LoanInstalment next = schedule.stream().filter(i -> !i.isPaid()).findFirst().orElse(null);
        int paid = (int) schedule.stream().filter(LoanInstalment::isPaid).count();
        int overdue = (int) schedule.stream().filter(i -> i.getStatus() == InstalmentStatus.OVERDUE).count();
        BigDecimal totalInterest = schedule.stream().map(LoanInstalment::getInterestPart).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal interestPaid = schedule.stream().filter(LoanInstalment::isPaid).map(LoanInstalment::getInterestPart)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Quote requested = loan.getStatus() == LoanStatus.APPLIED
                ? quote(loan.getAmountRequested(), loan.getType().getRate(), loan.getMonthsRequested(), loan.getMonthlyIncome())
                : null;
        return new LoanView(loan, schedule, next, paid, overdue, totalInterest, interestPaid, requested);
    }

    // ---------------------------------------------------------------- staff: decide

    /** Approves and disburses in one transaction: schedule saved, money credited, customer alerted. */
    @Transactional
    public Loan approve(Long loanId, String staff, BigDecimal principal, BigDecimal rate, Integer months) {
        Loan loan = loans.findByIdForUpdate(loanId).orElseThrow(() -> new NotFoundException("Loan not found"));
        if (loan.getStatus() != LoanStatus.APPLIED) {
            throw new InvalidRequestException("This application has already been " + loan.getStatus().name().toLowerCase());
        }
        List<String> errors = validate(loan.getType(), principal, months, loan.getEmployment(), loan.getMonthlyIncome());
        if (principal != null && principal.compareTo(loan.getAmountRequested()) > 0) {
            errors.add("Sanctioned amount can't be more than the Rs " + loan.getAmountRequested().toPlainString() + " requested");
        }
        if (rate == null || rate.compareTo(BigDecimal.ONE) < 0 || rate.compareTo(BigDecimal.valueOf(30)) > 0) {
            errors.add("Interest rate must be between 1% and 30% a year");
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" · ", errors));
        }
        Account account = accounts.findByIdForUpdate(loan.getAccount().getId()).orElseThrow();
        LocalDate today = LocalDate.now(clock);
        LocalDate firstEmi = EmiCalculator.firstEmiDate(today);
        BigDecimal amount = principal.setScale(2, RoundingMode.HALF_UP);
        List<EmiCalculator.Row> rows = EmiCalculator.schedule(amount, rate, months, firstEmi);
        LocalDateTime now = LocalDateTime.now(clock);

        loan.approve(amount, rate, months, rows.get(0).emi(), today, firstEmi, rows.get(rows.size() - 1).dueDate(), staff, now);
        for (EmiCalculator.Row r : rows) {
            instalments.save(new LoanInstalment(loan, r.number(), r.dueDate(), r.emi(), r.principal(), r.interest(), r.balance()));
        }
        account.credit(amount);
        transactions.save(new Transaction(account, TransactionType.LOAN_DISBURSAL, amount, UUID.randomUUID().toString(),
                null, "LOAN/" + loan.getReference() + "/" + loan.getType().getLabel() + " disbursed", now));
        notifications.notify(account.getCustomer(), "Your loan is approved",
                "Your " + loan.getType().getLabel().toLowerCase() + " " + loan.getReference() + " of Rs " + amount.toPlainString()
                        + " is approved and credited to your account. EMI Rs " + loan.getEmi().toPlainString() + " on the "
                        + firstEmi.getDayOfMonth() + ordinal(firstEmi.getDayOfMonth()) + " of every month, first on "
                        + firstEmi.format(DATE) + ", last on " + loan.getEndDate().format(DATE) + ".");
        return loan;
    }

    @Transactional
    public void reject(Long loanId, String staff, String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 200) {
            throw new InvalidRequestException("Give a reason for rejecting (up to 200 characters)");
        }
        Loan loan = loans.findByIdForUpdate(loanId).orElseThrow(() -> new NotFoundException("Loan not found"));
        loan.reject(reason.trim(), staff, LocalDateTime.now(clock));
        notifications.notify(loan.getAccount().getCustomer(), "Update on your loan application",
                "We couldn't approve your " + loan.getType().getLabel().toLowerCase() + " application " + loan.getReference()
                        + ": " + reason.trim() + ".");
    }

    // ---------------------------------------------------------------- repayments

    /** Runs every day at 9:40 (bank time zone); also at startup and from the staff Loans page. Returns EMIs paid. */
    @Scheduled(cron = "0 40 9 * * *", zone = "${bank.zone}")
    public int collectDueEmis() {
        LocalDate today = LocalDate.now(clock);
        int paid = 0;
        for (Long loanId : instalments.loansWithEmisDueBy(today)) {
            Integer n = tx.execute(status -> collect(loanId, today));
            paid += n == null ? 0 : n;
        }
        return paid;
    }

    /** Pays every due EMI of one loan, oldest first, stopping at the first one the balance can't cover. */
    private int collect(Long loanId, LocalDate today) {
        Loan loan = loans.findByIdForUpdate(loanId).orElseThrow();
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            return 0;
        }
        Account account = accounts.findByIdForUpdate(loan.getAccount().getId()).orElseThrow();
        int paid = 0;
        for (LoanInstalment i : instalments.findByLoanIdOrderByNumberAsc(loanId)) {
            if (i.isPaid()) {
                continue;
            }
            if (i.getDueDate().isAfter(today)) {
                break;
            }
            if (account.getStatus() != AccountStatus.ACTIVE || account.getBalance().compareTo(i.getEmi()) < 0) {
                if (i.markOverdue()) {
                    notifications.notify(account.getCustomer(), "EMI overdue",
                            "Your EMI " + i.getNumber() + "/" + loan.getTenureMonths() + " of Rs " + i.getEmi().toPlainString()
                                    + " for " + loan.getReference() + " due " + i.getDueDate().format(DATE)
                                    + " could not be debited (insufficient balance). Please add money; we'll retry daily.");
                }
                log.info("EMI {} of {} overdue", i.getNumber(), loan.getReference());
                break;
            }
            payInstalment(loan, account, i);
            paid++;
        }
        return paid;
    }

    /** Customer pays the next unpaid EMI now (early, or to clear an overdue one). */
    @Transactional
    public LoanInstalment payNext(String customerId, Long loanId) {
        Loan loan = loans.findByIdForUpdate(loanId)
                .filter(l -> l.getAccount().getCustomer().getCustomerId().equals(customerId))
                .orElseThrow(() -> new NotFoundException("Loan not found"));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new InvalidRequestException("This loan has no EMIs left to pay");
        }
        LoanInstalment next = instalments.findFirstByLoanIdAndStatusInOrderByNumberAsc(loanId,
                List.of(InstalmentStatus.DUE, InstalmentStatus.OVERDUE)).orElseThrow();
        Account account = accounts.findByIdForUpdate(loan.getAccount().getId()).orElseThrow();
        payInstalment(loan, account, next);
        return next;
    }

    private void payInstalment(Loan loan, Account account, LoanInstalment i) {
        LocalDateTime now = LocalDateTime.now(clock);
        account.debit(i.getEmi());
        transactions.save(new Transaction(account, TransactionType.LOAN_EMI, i.getEmi(), UUID.randomUUID().toString(), null,
                "EMI " + i.getNumber() + "/" + loan.getTenureMonths() + "/" + loan.getReference() + "/" + loan.getType().getLabel(), now));
        i.markPaid(now);
        loan.emiPaid(i.getBalanceAfter());
        if (loan.getStatus() == LoanStatus.CLOSED) {
            notifications.notify(account.getCustomer(), "Loan closed",
                    "Congratulations! You've paid every EMI of " + loan.getReference() + ". Your "
                            + loan.getType().getLabel().toLowerCase() + " is now closed.");
        } else {
            notifications.notify(account.getCustomer(), "EMI paid",
                    "EMI " + i.getNumber() + "/" + loan.getTenureMonths() + " of Rs " + i.getEmi().toPlainString() + " for "
                            + loan.getReference() + " paid. Principal still owed: Rs " + i.getBalanceAfter().toPlainString() + ".");
        }
    }

    // ---------------------------------------------------------------- public enquiries

    @Transactional
    public LoanEnquiry enquire(Enquiry e) {
        List<String> errors = validate(e.type(), e.amount(), e.type() == null ? null : e.type().getMinMonths(),
                e.employment(), e.monthlyIncome());
        if (e.name() == null || e.name().isBlank() || e.name().trim().length() > 80) errors.add("Enter your name");
        if (e.mobile() == null || !e.mobile().trim().matches("[6-9]\\d{9}")) errors.add("Enter a valid 10-digit mobile number");
        if (e.email() == null || !e.email().trim().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) errors.add("Enter a valid email address");
        if (e.city() == null || e.city().isBlank() || e.city().trim().length() > 50) errors.add("Enter your city");
        if (e.preferredTime() == null || !CALL_TIMES.contains(e.preferredTime())) errors.add("Choose a time for our call");
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" · ", errors));
        }
        String ref;
        do {
            ref = numbers.newReference("JBE");
        } while (enquiries.existsByReference(ref));
        LoanEnquiry saved = enquiries.save(new LoanEnquiry(ref, e.name().trim(), e.mobile().trim(), e.email().trim(),
                e.city().trim(), e.type(), e.amount(), e.employment(), e.monthlyIncome(), e.preferredTime(), LocalDateTime.now(clock)));
        notifications.lead(saved.getMobile(), saved.getEmail(), "We've received your loan enquiry",
                "Thanks " + saved.getName() + "! Our loan expert will call you on " + saved.getMobile() + " within 24 hours about your "
                        + e.type().getLabel().toLowerCase() + " (ref " + ref + ").");
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LoanEnquiry> recentEnquiries() {
        return enquiries.findTop100ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public long newEnquiries() {
        return enquiries.countByStatus(LoanEnquiry.Status.NEW);
    }

    @Transactional
    public void updateEnquiry(Long id, LoanEnquiry.Status status, String staff, String note) {
        if (status == null || status == LoanEnquiry.Status.NEW) {
            throw new InvalidRequestException("Choose contacted or closed");
        }
        if (status == LoanEnquiry.Status.CLOSED && (note == null || note.isBlank())) {
            throw new InvalidRequestException("Add a note explaining why the enquiry is closed");
        }
        enquiries.findById(id).orElseThrow(() -> new NotFoundException("Enquiry not found"))
                .update(status, staff, note, LocalDateTime.now(clock));
    }

    // ---------------------------------------------------------------- demo data support

    /**
     * Demo data only: a loan the customer already had, disbursed {@code monthsAgo} months ago, with every EMI due
     * up to today marked paid. The account balance and ledger are not touched.
     */
    @Transactional
    public Loan importExistingLoan(String customerId, LoanType type, BigDecimal principal, int months, int monthsAgo,
                                   String purpose, BigDecimal monthlyIncome) {
        Account account = account(customerId);
        LocalDate disbursed = LocalDate.now(clock).minusMonths(monthsAgo);
        LocalDate firstEmi = EmiCalculator.firstEmiDate(disbursed);
        List<EmiCalculator.Row> rows = EmiCalculator.schedule(principal, type.getRate(), months, firstEmi);
        Loan loan = new Loan(account, type, numbers.newReference("JBL"), principal, months, purpose, "Salaried",
                monthlyIncome, disbursed.atStartOfDay());
        loan.approve(principal, type.getRate(), months, rows.get(0).emi(), disbursed, firstEmi,
                rows.get(rows.size() - 1).dueDate(), "admin", disbursed.atStartOfDay());
        loans.save(loan);
        LocalDate today = LocalDate.now(clock);
        for (EmiCalculator.Row r : rows) {
            LoanInstalment i = instalments.save(new LoanInstalment(loan, r.number(), r.dueDate(), r.emi(), r.principal(),
                    r.interest(), r.balance()));
            if (!r.dueDate().isAfter(today)) {   // due on or before today: already paid, as the daily job would have
                i.markPaid(r.dueDate().atTime(9, 40));
                loan.emiPaid(r.balance());
            }
        }
        return loan;
    }

    /** Demo data only: an application waiting for a loan officer. */
    @Transactional
    public Loan importApplication(String customerId, Application a) {
        Account account = account(customerId);
        return loans.save(new Loan(account, a.type(), numbers.newReference("JBL"), a.amount(), a.months(), a.purpose(),
                a.employment(), a.monthlyIncome(), LocalDateTime.now(clock)));
    }

    // ---------------------------------------------------------------- helpers

    private List<String> validate(LoanType type, BigDecimal amount, Integer months, String employment, BigDecimal income) {
        List<String> errors = new ArrayList<>();
        if (type == null) {
            errors.add("Choose a loan type");
        } else {
            if (amount == null || amount.compareTo(type.getMinAmount()) < 0 || amount.compareTo(type.getMaxAmount()) > 0) {
                errors.add(type.getLabel() + " amount must be between Rs " + type.getMinAmount().toPlainString()
                        + " and Rs " + type.getMaxAmount().toPlainString());
            }
            if (months == null || months < type.getMinMonths() || months > type.getMaxMonths()) {
                errors.add(type.getLabel() + " tenure must be between " + type.getMinMonths() + " and " + type.getMaxMonths() + " months");
            }
        }
        if (employment == null || !EMPLOYMENT.contains(employment)) errors.add("Choose your employment type");
        if (income == null || income.compareTo(BigDecimal.valueOf(10_000)) < 0) errors.add("Monthly income must be at least Rs 10,000");
        return errors;
    }

    private static String ordinal(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }

    private Account account(String customerId) {
        return accounts.findByCustomerLogin(customerId).orElseThrow(() -> new NotFoundException("Account not found"));
    }
}
