package com.koustubh.bank.web;

import com.koustubh.bank.domain.Transaction;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** What the ATM prints after a transaction. Passed between requests as a flash attribute. */
public record Receipt(String title, BigDecimal amount, BigDecimal balanceAfter, String reference,
                      String counterpartyAccount, LocalDateTime time) implements Serializable {

    static Receipt of(Transaction t) {
        return new Receipt(t.getType().getLabel(), t.getAmount(), t.getBalanceAfter(), t.getReferenceId(),
                t.getCounterpartyAccount(), t.getCreatedAt());
    }
}
