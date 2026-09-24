package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A loan enquiry from the public Loans page, from someone who may not be a customer yet. */
@Entity
public class LoanEnquiry {

    public enum Status { NEW, CONTACTED, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference;
    private String name;
    private String mobile;
    private String email;
    private String city;

    @Enumerated(EnumType.STRING)
    private LoanType type;

    private BigDecimal amount;
    private String employment;
    private BigDecimal monthlyIncome;
    private String preferredTime;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String staffNote;
    private String handledBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected LoanEnquiry() {
    }

    public LoanEnquiry(String reference, String name, String mobile, String email, String city, LoanType type,
                       BigDecimal amount, String employment, BigDecimal monthlyIncome, String preferredTime,
                       LocalDateTime now) {
        this.reference = reference;
        this.name = name;
        this.mobile = mobile;
        this.email = email;
        this.city = city;
        this.type = type;
        this.amount = amount;
        this.employment = employment;
        this.monthlyIncome = monthlyIncome;
        this.preferredTime = preferredTime;
        this.status = Status.NEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(Status newStatus, String staff, String note, LocalDateTime now) {
        status = newStatus;
        handledBy = staff;
        if (note != null && !note.isBlank()) {
            staffNote = note.trim();
        }
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public String getName() { return name; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getCity() { return city; }
    public LoanType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getEmployment() { return employment; }
    public BigDecimal getMonthlyIncome() { return monthlyIncome; }
    public String getPreferredTime() { return preferredTime; }
    public Status getStatus() { return status; }
    public String getStaffNote() { return staffNote; }
    public String getHandledBy() { return handledBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
