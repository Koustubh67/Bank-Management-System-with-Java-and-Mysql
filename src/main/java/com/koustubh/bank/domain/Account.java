package com.koustubh.bank.domain;

import com.koustubh.bank.exception.AccountNotActiveException;
import com.koustubh.bank.exception.InsufficientFundsException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    private BigDecimal balance;

    private String services;

    private String declineReason;

    private String reviewedBy;

    private LocalDateTime reviewedAt;

    /** Optimistic locking: a concurrent update of the same row fails instead of silently overwriting. */
    @Version
    private Long version;

    private LocalDateTime createdAt;

    protected Account() {
    }

    public Account(String accountNumber, Customer customer, AccountType accountType, String services, LocalDateTime createdAt) {
        this.accountNumber = accountNumber;
        this.customer = customer;
        this.accountType = accountType;
        this.services = services;
        this.createdAt = createdAt;
        this.status = AccountStatus.PENDING;
        this.balance = BigDecimal.ZERO.setScale(2);
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        balance = balance.subtract(amount);
    }

    public void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(accountNumber, status);
        }
    }

    public void activate() {
        status = AccountStatus.ACTIVE;
    }

    public void freeze() {
        status = AccountStatus.FROZEN;
    }

    /** Records which staff member approved or declined the application. */
    public void reviewed(String staff, LocalDateTime at) {
        reviewedBy = staff;
        reviewedAt = at;
    }

    public void decline(String reason) {
        status = AccountStatus.DECLINED;
        declineReason = reason;
    }

    public Long getId() { return id; }
    public String getAccountNumber() { return accountNumber; }
    public Customer getCustomer() { return customer; }
    public AccountType getAccountType() { return accountType; }
    public AccountStatus getStatus() { return status; }
    public BigDecimal getBalance() { return balance; }
    public String getServices() { return services; }
    public String getDeclineReason() { return declineReason; }
    public String getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Shown on receipts, e.g. XXXXXXXX4821. */
    public String getMaskedNumber() {
        return "XXXXXXXX" + accountNumber.substring(accountNumber.length() - 4);
    }
}
