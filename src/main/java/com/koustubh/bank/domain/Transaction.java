package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One ledger entry. Entries are immutable: corrections are new entries, never edits. */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    private BigDecimal amount;

    private BigDecimal balanceAfter;

    /** Both legs of a transfer share the same reference id. */
    private String referenceId;

    private String counterpartyAccount;

    /** Narration shown on statements, e.g. "UPI/priya.3311@javabank/lunch". */
    private String remarks;

    private LocalDateTime createdAt;

    protected Transaction() {
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount, String referenceId,
                       String counterpartyAccount, LocalDateTime createdAt) {
        this(account, type, amount, referenceId, counterpartyAccount, null, createdAt);
    }

    public Transaction(Account account, TransactionType type, BigDecimal amount, String referenceId,
                       String counterpartyAccount, String remarks, LocalDateTime createdAt) {
        this.remarks = remarks;
        this.account = account;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = account.getBalance();
        this.referenceId = referenceId;
        this.counterpartyAccount = counterpartyAccount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getReferenceId() { return referenceId; }
    public String getCounterpartyAccount() { return counterpartyAccount; }
    public String getRemarks() { return remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
