package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** History of repo rate changes recorded by staff, and how many floating-rate loans each one repriced. */
@Entity
public class RepoRateChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal oldRate;
    private BigDecimal newRate;
    private LocalDate effectiveFrom;
    private String note;
    private String changedBy;
    private int loansRepriced;
    private LocalDateTime createdAt;

    protected RepoRateChange() {
    }

    public RepoRateChange(BigDecimal oldRate, BigDecimal newRate, LocalDate effectiveFrom, String note, String changedBy,
                          LocalDateTime now) {
        this.oldRate = oldRate;
        this.newRate = newRate;
        this.effectiveFrom = effectiveFrom;
        this.note = note;
        this.changedBy = changedBy;
        this.createdAt = now;
    }

    public void setLoansRepriced(int loansRepriced) {
        this.loansRepriced = loansRepriced;
    }

    public Long getId() { return id; }
    public BigDecimal getOldRate() { return oldRate; }
    public BigDecimal getNewRate() { return newRate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public String getNote() { return note; }
    public String getChangedBy() { return changedBy; }
    public int getLoansRepriced() { return loansRepriced; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
