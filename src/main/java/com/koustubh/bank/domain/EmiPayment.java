package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One attempt to pay a loan EMI, and its receipt once paid. Card and UPI payments start as PENDING and must be
 * completed within 5 minutes; account payments and auto-debits are paid at once. For cards only the network, last
 * four digits and name are kept: the full number and the CVV are never stored.
 */
@Entity
public class EmiPayment {

    public enum Method {
        AUTO_DEBIT("Auto-debit from savings account"),
        ACCOUNT("JavaBank savings account"),
        CARD("Debit / credit card"),
        UPI("UPI");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

    public enum Status { PENDING, PAID, EXPIRED, FAILED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Receipt number, e.g. JBR4821337712. */
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Loan loan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private LoanInstalment instalment;

    private int instalmentNumber;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Method method;

    @Enumerated(EnumType.STRING)
    private Status status;

    /** Set only once PAID; unique in the database, so an EMI can never be paid twice. */
    private Long paidInstalmentId;

    private String cardNetwork;
    private String cardLast4;
    private String cardHolder;
    private String otpHash;
    private int otpAttempts;

    /** UTR for UPI, authorisation code for cards, ledger reference for account debits. */
    private String transactionId;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime paidAt;

    @Version
    private Long version;

    protected EmiPayment() {
    }

    public EmiPayment(Loan loan, LoanInstalment instalment, Method method, String reference, LocalDateTime now,
                      LocalDateTime expiresAt) {
        this.loan = loan;
        this.instalment = instalment;
        this.instalmentNumber = instalment.getNumber();
        this.amount = instalment.getEmi();
        this.method = method;
        this.reference = reference;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public void card(String network, String last4, String holder, String otpHash) {
        this.cardNetwork = network;
        this.cardLast4 = last4;
        this.cardHolder = holder;
        this.otpHash = otpHash;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    /** Counts a wrong OTP and returns how many tries are left. */
    public int wrongOtp(int maxAttempts) {
        otpAttempts++;
        return Math.max(0, maxAttempts - otpAttempts);
    }

    public void paid(String transactionId, LocalDateTime now) {
        this.status = Status.PAID;
        this.transactionId = transactionId;
        this.paidAt = now;
        this.paidInstalmentId = instalment.getId();
        this.otpHash = null;
    }

    public void close(Status status, String reason) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Payment " + reference + " is already " + this.status);
        }
        this.status = status;
        this.failureReason = reason;
        this.otpHash = null;
    }

    /** How the money came in, as printed on the receipt, e.g. "Visa card •••• 1111". */
    public String getPaidWith() {
        return switch (method) {
            case CARD -> cardNetwork + " card •••• " + cardLast4;
            case UPI -> "UPI (QR code)";
            case ACCOUNT, AUTO_DEBIT -> method.getLabel() + " " + loan.getAccount().getShortNumber();
        };
    }

    /** What the transaction id is called for this method. */
    public String getTransactionLabel() {
        return switch (method) {
            case CARD -> "Authorisation code";
            case UPI -> "UPI transaction ID (UTR)";
            case ACCOUNT, AUTO_DEBIT -> "Ledger reference";
        };
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public Loan getLoan() { return loan; }
    public LoanInstalment getInstalment() { return instalment; }
    public int getInstalmentNumber() { return instalmentNumber; }
    public BigDecimal getAmount() { return amount; }
    public Method getMethod() { return method; }
    public Status getStatus() { return status; }
    public String getCardNetwork() { return cardNetwork; }
    public String getCardLast4() { return cardLast4; }
    public String getCardHolder() { return cardHolder; }
    public String getOtpHash() { return otpHash; }
    public int getOtpAttempts() { return otpAttempts; }
    public String getTransactionId() { return transactionId; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
