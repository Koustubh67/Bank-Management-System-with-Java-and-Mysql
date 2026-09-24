package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of a loan's repayment schedule. The amounts are set at approval. For floating-rate loans the EMIs not yet
 * paid are recalculated when the repo rate changes; paid ones never change.
 */
@Entity
public class LoanInstalment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Loan loan;

    private int number;
    private LocalDate dueDate;
    private BigDecimal emi;
    private BigDecimal principalPart;
    private BigDecimal interestPart;
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    private InstalmentStatus status;

    private LocalDateTime paidOn;

    protected LoanInstalment() {
    }

    public LoanInstalment(Loan loan, int number, LocalDate dueDate, BigDecimal emi, BigDecimal principalPart,
                          BigDecimal interestPart, BigDecimal balanceAfter) {
        this.loan = loan;
        this.number = number;
        this.dueDate = dueDate;
        this.emi = emi;
        this.principalPart = principalPart;
        this.interestPart = interestPart;
        this.balanceAfter = balanceAfter;
        this.status = InstalmentStatus.DUE;
    }

    public void markPaid(LocalDateTime when) {
        status = InstalmentStatus.PAID;
        paidOn = when;
    }

    /** Returns true only the first time, so the customer is alerted once per missed EMI. */
    public boolean markOverdue() {
        if (status == InstalmentStatus.DUE) {
            status = InstalmentStatus.OVERDUE;
            return true;
        }
        return false;
    }

    /** New amounts after a rate reset. Paid EMIs are history and can't be changed. */
    public void reschedule(BigDecimal emi, BigDecimal principalPart, BigDecimal interestPart, BigDecimal balanceAfter) {
        if (isPaid()) {
            throw new IllegalStateException("EMI " + number + " is already paid");
        }
        this.emi = emi;
        this.principalPart = principalPart;
        this.interestPart = interestPart;
        this.balanceAfter = balanceAfter;
    }

    public boolean isPaid() {
        return status == InstalmentStatus.PAID;
    }

    public Long getId() { return id; }
    public Loan getLoan() { return loan; }
    public int getNumber() { return number; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getEmi() { return emi; }
    public BigDecimal getPrincipalPart() { return principalPart; }
    public BigDecimal getInterestPart() { return interestPart; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public InstalmentStatus getStatus() { return status; }
    public LocalDateTime getPaidOn() { return paidOn; }
}
