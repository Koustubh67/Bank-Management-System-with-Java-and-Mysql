package com.koustubh.bank.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Works out which notes an ATM pays out, using the fewest notes (₹500, then ₹200, then ₹100). */
public final class CashDispenser {

    public static final int[] DENOMINATIONS = {500, 200, 100};

    public record NoteBundle(int value, int count) implements Serializable {
    }

    private CashDispenser() {
    }

    /** {@code amount} must be a positive multiple of 100, which the ATM already checks. */
    public static List<NoteBundle> breakdown(BigDecimal amount) {
        int remaining = amount.intValueExact();
        List<NoteBundle> notes = new ArrayList<>();
        for (int value : DENOMINATIONS) {
            int count = remaining / value;
            if (count > 0) {
                notes.add(new NoteBundle(value, count));
                remaining -= count * value;
            }
        }
        if (remaining != 0) {
            throw new IllegalArgumentException("Amount must be a multiple of 100: " + amount);
        }
        return List.copyOf(notes);
    }
}
