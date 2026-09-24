package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class InsurancePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    private InsurancePlan plan;

    private String policyNumber;
    private String insurer;
    private BigDecimal cover;
    private BigDecimal annualPremium;
    /** Nominee name or vehicle number, depending on the plan. */
    private String details;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;

    protected InsurancePolicy() {
    }

    public InsurancePolicy(Account account, InsurancePlan plan, String insurer, String policyNumber, BigDecimal cover,
                           BigDecimal annualPremium, String details, LocalDate start, LocalDateTime now) {
        this.account = account;
        this.insurer = insurer;
        this.plan = plan;
        this.policyNumber = policyNumber;
        this.cover = cover;
        this.annualPremium = annualPremium;
        this.details = details;
        this.startDate = start;
        this.endDate = start.plusYears(1).minusDays(1);
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public InsurancePlan getPlan() { return plan; }
    public String getPolicyNumber() { return policyNumber; }
    public String getInsurer() { return insurer; }

    public boolean isActiveOn(LocalDate day) {
        return !day.isBefore(startDate) && !day.isAfter(endDate);
    }
    public BigDecimal getCover() { return cover; }
    public BigDecimal getAnnualPremium() { return annualPremium; }
    public String getDetails() { return details; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
