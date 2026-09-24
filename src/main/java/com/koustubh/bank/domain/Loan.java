package com.koustubh.bank.domain;

import com.koustubh.bank.exception.InvalidRequestException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A loan from application to closure. Sanction terms are set once, when a loan officer approves it. For a
 * floating-rate loan the rate is the repo rate plus {@code spreadPercent}; when the repo rate changes the rate and EMI
 * are reset (see LendingRateService), but the spread and the end date never change.
 */
@Entity
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    private LoanType type;

    @Enumerated(EnumType.STRING)
    private RateType rateType;

    private String reference;
    private BigDecimal amountRequested;
    private int monthsRequested;
    private String purpose;
    private String employment;
    private BigDecimal monthlyIncome;

    @Enumerated(EnumType.STRING)
    private LoanStatus status;

    private BigDecimal principal;
    private BigDecimal ratePercent;
    private BigDecimal spreadPercent;
    private Integer tenureMonths;
    private BigDecimal emi;
    private LocalDate disbursedOn;
    private LocalDate firstEmiDate;
    private LocalDate endDate;
    private BigDecimal outstanding;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectReason;
    private LocalDateTime createdAt;

    /** Optimistic locking: two officers acting on the same application can't both succeed. */
    @Version
    private Long version;

    protected Loan() {
    }

    public Loan(Account account, LoanType type, RateType rateType, String reference, BigDecimal amountRequested,
                int monthsRequested, String purpose, String employment, BigDecimal monthlyIncome, LocalDateTime now) {
        this.account = account;
        this.type = type;
        this.rateType = rateType;
        this.reference = reference;
        this.amountRequested = amountRequested;
        this.monthsRequested = monthsRequested;
        this.purpose = purpose;
        this.employment = employment;
        this.monthlyIncome = monthlyIncome;
        this.status = LoanStatus.APPLIED;
        this.createdAt = now;
    }

    /**
     * Sets the sanctioned terms and marks the loan active. The schedule and disbursal are done by LoanService.
     * {@code spread} is the part of the rate above the repo rate; only floating-rate loans have one.
     */
    public void approve(BigDecimal principal, BigDecimal rate, BigDecimal spread, int months, BigDecimal emi,
                        LocalDate disbursedOn, LocalDate firstEmiDate, LocalDate endDate, String staff, LocalDateTime now) {
        requireStatus(LoanStatus.APPLIED, "Only applications waiting for review can be approved");
        if ((rateType == RateType.FLOATING) != (spread != null)) {
            throw new IllegalArgumentException("Floating-rate loans need a spread over the repo rate; fixed-rate loans don't");
        }
        this.principal = principal;
        this.ratePercent = rate;
        this.spreadPercent = spread;
        this.tenureMonths = months;
        this.emi = emi;
        this.disbursedOn = disbursedOn;
        this.firstEmiDate = firstEmiDate;
        this.endDate = endDate;
        this.outstanding = principal;
        this.reviewedBy = staff;
        this.reviewedAt = now;
        this.status = LoanStatus.ACTIVE;
    }

    public void reject(String reason, String staff, LocalDateTime now) {
        requireStatus(LoanStatus.APPLIED, "Only applications waiting for review can be rejected");
        this.rejectReason = reason;
        this.reviewedBy = staff;
        this.reviewedAt = now;
        this.status = LoanStatus.REJECTED;
    }

    /** An EMI was paid; the principal still owed is now {@code balanceAfter}. Closes the loan at zero. */
    public void emiPaid(BigDecimal balanceAfter) {
        requireStatus(LoanStatus.ACTIVE, "This loan is not active");
        outstanding = balanceAfter;
        if (balanceAfter.signum() == 0) {
            status = LoanStatus.CLOSED;
        }
    }

    /** The repo rate changed: new rate, and the EMI of the remaining schedule. Only for active floating-rate loans. */
    public void reprice(BigDecimal newRate, BigDecimal newEmi) {
        requireStatus(LoanStatus.ACTIVE, "This loan is not active");
        if (rateType != RateType.FLOATING) {
            throw new InvalidRequestException("Only floating-rate loans follow the repo rate");
        }
        this.ratePercent = newRate;
        this.emi = newEmi;
    }

    private void requireStatus(LoanStatus expected, String message) {
        if (status != expected) {
            throw new InvalidRequestException(message);
        }
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public LoanType getType() { return type; }
    public RateType getRateType() { return rateType; }
    public String getReference() { return reference; }
    public BigDecimal getAmountRequested() { return amountRequested; }
    public int getMonthsRequested() { return monthsRequested; }
    public String getPurpose() { return purpose; }
    public String getEmployment() { return employment; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public LoanStatus getStatus() { return status; }
    public BigDecimal getPrincipal() { return principal; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public BigDecimal getSpreadPercent() { return spreadPercent; }
    public Integer getTenureMonths() { return tenureMonths; }
    public BigDecimal getEmi() { return emi; }
    public LocalDate getDisbursedOn() { return disbursedOn; }
    public LocalDate getFirstEmiDate() { return firstEmiDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getOutstanding() { return outstanding; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public String getRejectReason() { return rejectReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Tenure shown to people, e.g. "5 years" or "18 months". */
    public String getTenureLabel() {
        int m = tenureMonths != null ? tenureMonths : monthsRequested;
        return m % 12 == 0 ? m / 12 + (m == 12 ? " year" : " years") : m + " months";
    }
}
