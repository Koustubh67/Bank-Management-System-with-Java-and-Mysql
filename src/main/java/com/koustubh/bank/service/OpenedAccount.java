package com.koustubh.bank.service;

/** Returned once after account opening. The PIN is shown to the customer and never stored in plain text. */
public record OpenedAccount(String customerId, String accountNumber, String cardNumber, String pin) {
}
