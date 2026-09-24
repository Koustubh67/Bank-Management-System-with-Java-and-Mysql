package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** One reset of a floating-rate loan: the new rate and EMI apply from EMI {@code fromInstalment} onwards. */
@Entity
public class LoanRateChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Loan loan;

    private int fromInstalment;
    private LocalDate effectiveFrom;
    private BigDecimal oldRate;
    private BigDecimal newRate;
    private BigDecimal oldEmi;
    private BigDecimal newEmi;
    private BigDecimal repoRate;
    private String changedBy;
    private LocalDateTime createdAt;

    protected LoanRateChange() {
    }

    public LoanRateChange(Loan loan, int fromInstalment, LocalDate effectiveFrom, BigDecimal oldRate, BigDecimal newRate,
                          BigDecimal oldEmi, BigDecimal newEmi, BigDecimal repoRate, String changedBy, LocalDateTime now) {
        this.loan = loan;
        this.fromInstalment = fromInstalment;
        this.effectiveFrom = effectiveFrom;
        this.oldRate = oldRate;
        this.newRate = newRate;
        this.oldEmi = oldEmi;
        this.newEmi = newEmi;
        this.repoRate = repoRate;
        this.changedBy = changedBy;
        this.createdAt = now;
    }

    public boolean isIncrease() {
        return newRate.compareTo(oldRate) > 0;
    }

    public Long getId() { return id; }
    public Loan getLoan() { return loan; }
    public int getFromInstalment() { return fromInstalment; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public BigDecimal getOldRate() { return oldRate; }
    public BigDecimal getNewRate() { return newRate; }
    public BigDecimal getOldEmi() { return oldEmi; }
    public BigDecimal getNewEmi() { return newEmi; }
    public BigDecimal getRepoRate() { return repoRate; }
    public String getChangedBy() { return changedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
