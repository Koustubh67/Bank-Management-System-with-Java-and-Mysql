package com.koustubh.bank.web;

import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.service.CashDispenser;
import com.koustubh.bank.service.CashDispenser.NoteBundle;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * What the ATM shows and prints after a transaction. Passed between requests as a flash attribute.
 * {@code kind} tells the screen which animation to play: cash coming out, cash going in, or just a receipt.
 */
public record Receipt(String title, String kind, BigDecimal amount, BigDecimal balanceAfter, String reference,
                      String counterpartyAccount, List<NoteBundle> notes, LocalDateTime time) implements Serializable {

    public static final String CASH_OUT = "CASH_OUT";
    public static final String CASH_IN = "CASH_IN";
    public static final String TRANSFER = "TRANSFER";

    static Receipt of(Transaction t) {
        String kind = t.getType() == TransactionType.WITHDRAWAL ? CASH_OUT
                : t.getType() == TransactionType.DEPOSIT ? CASH_IN : TRANSFER;
        List<NoteBundle> notes = kind.equals(TRANSFER) ? List.of() : CashDispenser.breakdown(t.getAmount());
        return new Receipt(t.getType().getLabel(), kind, t.getAmount(), t.getBalanceAfter(), t.getReferenceId(),
                t.getCounterpartyAccount(), notes, t.getCreatedAt());
    }

    /** Every note in order, e.g. "500,500,200", for the dispensing animation. */
    public String getNoteList() {
        return notes.stream()
                .flatMap(n -> IntStream.range(0, n.count()).mapToObj(i -> String.valueOf(n.value())))
                .collect(Collectors.joining(","));
    }
}
