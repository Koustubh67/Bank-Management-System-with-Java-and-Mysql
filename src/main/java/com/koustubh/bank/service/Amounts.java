package com.koustubh.bank.service;

import com.koustubh.bank.exception.InvalidRequestException;

import java.math.BigDecimal;

/** Shared checks for money amounts entered by customers. */
final class Amounts {

    static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Amounts() {
    }

    static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidRequestException("Amount can have at most 2 decimal places");
        }
    }

    /** ATMs only handle notes, so cash amounts must be whole hundreds. */
    static void requireCashAmount(BigDecimal amount) {
        requirePositive(amount);
        if (amount.remainder(HUNDRED).signum() != 0) {
            throw new InvalidRequestException("Amount must be a multiple of Rs 100");
        }
    }
}
