package com.koustubh.bank.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The RBI repo rate that floating-rate loans are linked to. A single row (id 1): loan approvals and repo rate changes
 * both lock it, so a loan can never be priced off a rate that is being changed at the same moment.
 */
@Entity
@Table(name = "repo_rate")
public class RepoRate {

    public static final long ID = 1L;

    @Id
    private Long id;

    private BigDecimal ratePercent;
    private LocalDate effectiveFrom;
    private String updatedBy;
    private LocalDateTime updatedAt;

    protected RepoRate() {
    }

    public void change(BigDecimal rate, LocalDate from, String staff, LocalDateTime now) {
        this.ratePercent = rate;
        this.effectiveFrom = from;
        this.updatedBy = staff;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public BigDecimal getRatePercent() { return ratePercent; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public String getUpdatedBy() { return updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
