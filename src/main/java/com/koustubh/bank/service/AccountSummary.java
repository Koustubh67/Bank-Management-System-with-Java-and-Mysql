package com.koustubh.bank.service;

import java.math.BigDecimal;

/** What the ATM screens show about the logged-in customer's account. */
public record AccountSummary(String customerName, String accountNumber, String maskedAccountNumber,
                             String accountType, String maskedCardNumber, BigDecimal balance) {
}
