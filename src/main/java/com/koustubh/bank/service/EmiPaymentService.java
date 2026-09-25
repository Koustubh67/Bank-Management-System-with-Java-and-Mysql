package com.koustubh.bank.service;

import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paying loan EMIs: from the savings account, by card (card details, then an OTP) or by UPI (scan a QR code), plus
 * the daily auto-debit.
 * <ul>
 *   <li>The customer always pays the oldest unpaid EMI. Starting a new payment cancels any earlier one still pending
 *       for the same loan.</li>
 *   <li>Card and UPI payments must be completed within 5 minutes; after that they expire and nothing is charged.</li>
 *   <li>Completing a payment locks the loan, then checks the EMI is still unpaid and still the same amount (a repo rate
 *       reset can change it). The database also refuses a second successful payment for the same EMI.</li>
 *   <li>Cards: the number is checked with the Luhn algorithm, and expiry and CVV are validated, with every problem
 *       reported at once. Only the network, last 4 digits and name are stored, never the full number or the CVV. An
 *       OTP (BCrypt-hashed, 3 tries) confirms the payment.</li>
 *   <li>Every paid EMI gets a receipt, sent to the customer by SMS and email.</li>
 * </ul>
 */
@Service
public class EmiPaymentService {

    public static final Duration TIME_TO_PAY = Duration.ofMinutes(5);
    public static final int OTP_TRIES = 3;
    /** The bank's collection UPI ID in the QR code. */
    public static final String COLLECTION_VPA = "loans.javabank@javabank";
    /** JavaBank's own debit cards are paid from the savings account, not through the card gateway. */
    private static final String JAVABANK_BIN = "504093";
    private static final Pattern EXPIRY = Pattern.compile("^(0[1-9]|1[0-2])\\s*/?\\s*(\\d{2}|\\d{4})$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public record Card(String number, String holder, String expiry, String cvv) {
    }

    /** A card payment waiting for its OTP. The OTP is returned only so the demo can show the SMS on screen. */
    public record CardStarted(EmiPayment payment, String otp) {
    }

    /** The EMI the customer would pay now. */
    public record Due(Loan loan, LoanInstalment instalment, Account account) {

        public boolean isOverdue() {
            return instalment.getStatus() == InstalmentStatus.OVERDUE;
        }

        public boolean isAffordable() {
            return account.getBalance().compareTo(instalment.getEmi()) >= 0;
        }
    }

    /** Everything printed on a receipt. {@code next} is null once the loan is closed. */
    public record Receipt(EmiPayment payment, Loan loan, Customer customer, LoanInstalment instalment, LoanInstalment next,
                          int paid) {
    }

    private final EmiPaymentRepository payments;
    private final LoanRepository loans;
    private final LoanInstalmentRepository instalments;
    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final NumberGenerator numbers;
    private final NotificationService notifications;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public EmiPaymentService(EmiPaymentRepository payments, LoanRepository loans, LoanInstalmentRepository instalments,
                             AccountRepository accounts, TransactionRepository transactions, NumberGenerator numbers,
                             NotificationService notifications, PasswordEncoder encoder, Clock clock) {
        this.payments = payments;
        this.loans = loans;
        this.instalments = instalments;
        this.accounts = accounts;
        this.transactions = transactions;
        this.numbers = numbers;
        this.notifications = notifications;
        this.encoder = encoder;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- what is due

    @Transactional(readOnly = true)
    public Due due(String customerId, Long loanId) {
        Loan loan = loans.findWithCustomerById(loanId).filter(l -> ownedBy(l, customerId))
                .orElseThrow(() -> new NotFoundException("Loan not found"));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new InvalidRequestException("This loan has no EMIs left to pay");
        }
        return new Due(loan, nextUnpaid(loan), loan.getAccount());
    }

    // ---------------------------------------------------------------- savings account

    /** Pays the next EMI from the savings account at once. */
    @Transactional
    public EmiPayment payFromAccount(String customerId, Long loanId) {
        Loan loan = lockOwnLoan(customerId, loanId);
        LoanInstalment i = nextUnpaid(loan);
        Account account = accounts.findByIdForUpdate(loan.getAccount().getId()).orElseThrow();
        cancelPending(loan, "Paid from the savings account instead");
        return settle(loan, i, new EmiPayment(loan, i, EmiPayment.Method.ACCOUNT, newReference(), now(), null), account, null);
    }

    /** The daily auto-debit (see LoanService). The caller holds the loan and account locks. */
    public EmiPayment autoDebit(Loan loan, LoanInstalment i, Account account) {
        return settle(loan, i, new EmiPayment(loan, i, EmiPayment.Method.AUTO_DEBIT, newReference(), now(), null), account, null);
    }

    /** Demo data only: the receipt of an EMI that was already paid. No alert, no ledger entry. */
    public void recordPaid(Loan loan, LoanInstalment i, LocalDateTime paidAt) {
        EmiPayment p = new EmiPayment(loan, i, EmiPayment.Method.AUTO_DEBIT, newReference(), paidAt, null);
        p.paid(UUID.randomUUID().toString(), paidAt);
        payments.save(p);
    }

    // ---------------------------------------------------------------- card

    /** Step 1: checks the card and sends an OTP. The payment then waits up to 5 minutes for the OTP. */
    @Transactional
    public CardStarted startCard(String customerId, Long loanId, Card card) {
        List<String> errors = new ArrayList<>();
        String digits = card.number() == null ? "" : card.number().replaceAll("[\\s-]", "");
        boolean wellFormed = digits.matches("\\d{12,19}");
        String network = wellFormed ? network(digits) : "Card";
        if (!wellFormed || !luhn(digits)) {
            errors.add("Enter a valid card number");
        } else if (digits.startsWith(JAVABANK_BIN)) {
            errors.add("This is a JavaBank debit card: choose \"Savings account\" to pay straight from your account");
        }
        String holder = card.holder() == null ? "" : card.holder().trim().replaceAll("\\s+", " ");
        if (!holder.matches("[A-Za-z][A-Za-z .']{1,79}")) {
            errors.add("Enter the name as printed on the card");
        }
        Matcher expiry = EXPIRY.matcher(card.expiry() == null ? "" : card.expiry().trim());
        if (!expiry.matches()) {
            errors.add("Enter the expiry date as MM/YY");
        } else {
            int year = Integer.parseInt(expiry.group(2));
            YearMonth expires = YearMonth.of(year < 100 ? 2000 + year : year, Integer.parseInt(expiry.group(1)));
            YearMonth thisMonth = YearMonth.now(clock);
            if (expires.isBefore(thisMonth)) {
                errors.add("This card has expired");
            } else if (expires.isAfter(thisMonth.plusYears(20))) {
                errors.add("Check the expiry date");
            }
        }
        int cvvLength = "Amex".equals(network) ? 4 : 3;
        if (card.cvv() == null || !card.cvv().trim().matches("\\d{" + cvvLength + "}")) {
            errors.add("Enter the " + cvvLength + "-digit CVV from the back of the card");
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(String.join(" · ", errors));
        }

        Loan loan = lockOwnLoan(customerId, loanId);
        LoanInstalment i = nextUnpaid(loan);
        cancelPending(loan, "Replaced by a new payment");
        LocalDateTime now = now();
        EmiPayment p = new EmiPayment(loan, i, EmiPayment.Method.CARD, newReference(), now, now.plus(TIME_TO_PAY));
        String otp = numbers.newSixDigits();
        p.card(network, digits.substring(digits.length() - 4), holder.toUpperCase(), encoder.encode(otp));
        payments.save(p);
        notifications.sendOtp(loan.getAccount().getCustomer(), otp, "pay Rs " + i.getEmi().toPlainString() + " (EMI "
                + i.getNumber() + " of " + loan.getReference() + ") with your " + network + " card ending " + p.getCardLast4());
        return new CardStarted(p, otp);
    }

    /** Step 2: the OTP. A wrong OTP uses up one of 3 tries; the third wrong one stops the payment. */
    @Transactional(noRollbackFor = BankException.class)
    public EmiPayment confirmCard(String customerId, String reference, String otp) {
        EmiPayment p = lockOwnPayment(customerId, reference);
        if (p.getStatus() == EmiPayment.Status.PAID) {
            return p;
        }
        requirePending(p, EmiPayment.Method.CARD, "The OTP was not entered within 5 minutes");
        if (otp == null || !otp.trim().matches("\\d{6}") || !encoder.matches(otp.trim(), p.getOtpHash())) {
            int left = p.wrongOtp(OTP_TRIES);
            if (left == 0) {
                p.close(EmiPayment.Status.FAILED, "Wrong OTP entered " + OTP_TRIES + " times");
                throw new InvalidRequestException("Wrong OTP entered " + OTP_TRIES + " times, so the payment was stopped. Nothing was charged");
            }
            throw new InvalidRequestException("Incorrect OTP. " + left + (left == 1 ? " try" : " tries") + " left");
        }
        return complete(p, numbers.newSixDigits());
    }

    // ---------------------------------------------------------------- UPI

    /** Creates a UPI payment request; its QR code can be paid for 5 minutes. */
    @Transactional
    public EmiPayment startUpi(String customerId, Long loanId) {
        Loan loan = lockOwnLoan(customerId, loanId);
        LoanInstalment i = nextUnpaid(loan);
        cancelPending(loan, "Replaced by a new payment");
        LocalDateTime now = now();
        return payments.save(new EmiPayment(loan, i, EmiPayment.Method.UPI, newReference(), now, now.plus(TIME_TO_PAY)));
    }

    /** The standard UPI deep link in the QR code: payee, amount and our reference, readable by any UPI app. */
    public String upiLink(EmiPayment p) {
        return "upi://pay?pa=" + COLLECTION_VPA + "&pn=" + encode("JavaBank Loans") + "&am=" + p.getAmount().toPlainString()
                + "&cu=INR&tn=" + encode("EMI " + p.getInstalmentNumber() + " " + p.getLoan().getReference())
                + "&tr=" + p.getReference();
    }

    /**
     * The customer paid the QR code. In a real bank the UPI network calls the bank when the customer approves the
     * payment in their UPI app; this demo has no UPI network, so the "I've paid" button stands in for that callback.
     */
    @Transactional(noRollbackFor = BankException.class)
    public EmiPayment upiPaid(String customerId, String reference) {
        EmiPayment p = lockOwnPayment(customerId, reference);
        if (p.getStatus() == EmiPayment.Status.PAID) {
            return p;
        }
        requirePending(p, EmiPayment.Method.UPI, "The QR code was not paid within 5 minutes");
        return complete(p, numbers.newUtr());
    }

    // ---------------------------------------------------------------- status, cancel, receipts

    /** The payment as it stands now. A pending one past its 5 minutes is marked expired first. */
    @Transactional
    public EmiPayment current(String customerId, String reference) {
        EmiPayment p = ownPayment(customerId, reference);
        if (p.isPending() && p.isExpired(now())) {
            p = lockOwnPayment(customerId, reference);
            if (p.isPending()) {
                p.close(EmiPayment.Status.EXPIRED, "Not paid within 5 minutes");
            }
        }
        return p;
    }

    @Transactional
    public EmiPayment cancel(String customerId, String reference) {
        EmiPayment p = lockOwnPayment(customerId, reference);
        if (p.isPending()) {
            p.close(EmiPayment.Status.CANCELLED, "Cancelled by you");
        }
        return p;
    }

    @Transactional(readOnly = true)
    public Receipt receipt(String customerId, String reference) {
        return receiptOf(ownPayment(customerId, reference));
    }

    @Transactional(readOnly = true)
    public Receipt receiptForStaff(String reference) {
        return receiptOf(payments.findByReference(reference).orElseThrow(() -> new NotFoundException("Receipt not found")));
    }

    @Transactional(readOnly = true)
    public List<EmiPayment> paymentsOf(Long loanId) {
        return payments.findByLoanIdOrderByIdDesc(loanId);
    }

    /** Tidies up card and UPI payments left unfinished (each is also checked when it is used). */
    @Scheduled(fixedDelay = 60_000)
    public int expireStale() {
        return payments.expireOlderThan(now());
    }

    // ---------------------------------------------------------------- the one place an EMI gets paid

    /** Card or UPI money has arrived: checks the EMI is still due at the same amount, then settles it. */
    private EmiPayment complete(EmiPayment p, String transactionId) {
        Loan loan = p.getLoan();
        LoanInstalment i = p.getInstalment();
        if (loan.getStatus() != LoanStatus.ACTIVE || i.isPaid()) {
            p.close(EmiPayment.Status.CANCELLED, "EMI " + i.getNumber() + " was already paid, so nothing was charged");
            throw new InvalidRequestException("EMI " + i.getNumber() + " is already paid, so this payment was cancelled. Nothing was charged");
        }
        if (i.getEmi().compareTo(p.getAmount()) != 0) {
            p.close(EmiPayment.Status.CANCELLED, "The EMI changed after an interest rate reset");
            throw new InvalidRequestException("After an interest rate change this EMI is now Rs " + i.getEmi().toPlainString()
                    + ", so this payment was cancelled and nothing was charged. Please pay again");
        }
        return settle(loan, i, p, null, transactionId);
    }

    /**
     * Marks the EMI paid and issues the receipt. With {@code account} the money is debited from the savings account
     * through the ledger; without it the money came from outside (card or UPI) and {@code transactionId} is the
     * network's reference.
     */
    private EmiPayment settle(Loan loan, LoanInstalment i, EmiPayment p, Account account, String transactionId) {
        LocalDateTime now = now();
        String txn = transactionId;
        if (account != null) {
            account.debit(i.getEmi());
            txn = UUID.randomUUID().toString();
            transactions.save(new Transaction(account, TransactionType.LOAN_EMI, i.getEmi(), txn, null,
                    "EMI " + i.getNumber() + "/" + loan.getTenureMonths() + "/" + loan.getReference() + "/" + loan.getType().getLabel(), now));
        }
        i.markPaid(now);
        loan.emiPaid(i.getBalanceAfter());
        p.paid(txn, now);
        payments.save(p);

        String paid = "EMI " + i.getNumber() + "/" + loan.getTenureMonths() + " of Rs " + i.getEmi().toPlainString() + " for your "
                + loan.getType().getLabel().toLowerCase() + " " + loan.getReference() + " is paid (" + p.getPaidWith() + ", "
                + now.format(DATE_TIME) + "). Receipt " + p.getReference() + ", " + p.getTransactionLabel() + " " + txn + ".";
        if (loan.getStatus() == LoanStatus.CLOSED) {
            notifications.notify(loan.getAccount().getCustomer(), "Loan closed",
                    "Congratulations! " + paid + " That was your last EMI: your " + loan.getType().getLabel().toLowerCase() + " is now closed.");
        } else {
            LoanInstalment next = nextUnpaid(loan);
            notifications.notify(loan.getAccount().getCustomer(), "EMI paid", paid + " Principal still owed: Rs "
                    + i.getBalanceAfter().toPlainString() + ". Next EMI Rs " + next.getEmi().toPlainString() + " on "
                    + next.getDueDate().format(DATE) + ".");
        }
        return p;
    }

    // ---------------------------------------------------------------- helpers

    private Receipt receiptOf(EmiPayment p) {
        if (p.getStatus() != EmiPayment.Status.PAID) {
            throw new NotFoundException("There is no receipt: this payment was not completed");
        }
        Loan loan = p.getLoan();
        List<LoanInstalment> schedule = instalments.findByLoanIdOrderByNumberAsc(loan.getId());
        LoanInstalment next = schedule.stream().filter(x -> !x.isPaid()).findFirst().orElse(null);
        int paid = (int) schedule.stream().filter(LoanInstalment::isPaid).count();
        return new Receipt(p, loan, loan.getAccount().getCustomer(), p.getInstalment(), next, paid);
    }

    private void requirePending(EmiPayment p, EmiPayment.Method method, String expiredReason) {
        if (p.getMethod() != method) {
            throw new InvalidRequestException("This is not a " + method.getLabel() + " payment");
        }
        if (!p.isPending()) {
            throw new InvalidRequestException("This payment is " + p.getStatus().name().toLowerCase()
                    + (p.getFailureReason() == null ? "" : " (" + p.getFailureReason() + ")") + ". Nothing was charged");
        }
        if (p.isExpired(now())) {
            p.close(EmiPayment.Status.EXPIRED, expiredReason);
            throw new InvalidRequestException("The 5 minutes to pay are over, so this payment expired. Nothing was charged: please start again");
        }
    }

    private void cancelPending(Loan loan, String reason) {
        payments.findByLoanIdAndStatus(loan.getId(), EmiPayment.Status.PENDING).forEach(p -> p.close(EmiPayment.Status.CANCELLED, reason));
    }

    private Loan lockOwnLoan(String customerId, Long loanId) {
        Loan loan = loans.findByIdForUpdate(loanId).filter(l -> ownedBy(l, customerId))
                .orElseThrow(() -> new NotFoundException("Loan not found"));
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            throw new InvalidRequestException("This loan has no EMIs left to pay");
        }
        return loan;
    }

    /**
     * Every change to a loan's payments happens under the loan's row lock, so the payment is (re)read after taking
     * it: a payment completed in another tab a moment earlier is seen as paid, not overwritten.
     */
    private EmiPayment lockOwnPayment(String customerId, String reference) {
        Long loanId = payments.findLoanIdByReference(reference).orElseThrow(() -> new NotFoundException("Payment not found"));
        loans.findByIdForUpdate(loanId);
        EmiPayment p = ownPayment(customerId, reference);
        entityManager.refresh(p);
        return p;
    }

    private EmiPayment ownPayment(String customerId, String reference) {
        return payments.findByReference(reference).filter(p -> ownedBy(p.getLoan(), customerId))
                .orElseThrow(() -> new NotFoundException("Payment not found"));
    }

    private LoanInstalment nextUnpaid(Loan loan) {
        return instalments.findFirstByLoanIdAndStatusInOrderByNumberAsc(loan.getId(),
                List.of(InstalmentStatus.DUE, InstalmentStatus.OVERDUE))
                .orElseThrow(() -> new InvalidRequestException("This loan has no EMIs left to pay"));
    }

    private static boolean ownedBy(Loan loan, String customerId) {
        return loan.getAccount().getCustomer().getCustomerId().equals(customerId);
    }

    private String newReference() {
        String ref;
        do {
            ref = numbers.newReference("JBR");
        } while (payments.existsByReference(ref));
        return ref;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Card network from the first digits (IIN ranges). */
    static String network(String digits) {
        if (digits.startsWith("34") || digits.startsWith("37")) return "Amex";
        if (digits.startsWith("4")) return "Visa";
        if (digits.length() >= 4) {
            int two = Integer.parseInt(digits.substring(0, 2));
            int four = Integer.parseInt(digits.substring(0, 4));
            if ((two >= 51 && two <= 55) || (four >= 2221 && four <= 2720)) return "Mastercard";
        }
        if (digits.startsWith("60") || digits.startsWith("65") || digits.startsWith("81") || digits.startsWith("82")
                || digits.startsWith("508")) return "RuPay";
        return "Card";
    }

    /** Luhn checksum, which every real card number passes: it catches typos before anything is sent. */
    static boolean luhn(String digits) {
        int sum = 0;
        boolean doubleIt = false;
        for (int n = digits.length() - 1; n >= 0; n--) {
            int d = digits.charAt(n) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return sum % 10 == 0;
    }
}
