package com.koustubh.bank.exception;

/** A business rule was broken. The message is safe to show to the customer. */
public class BankException extends RuntimeException {
    public BankException(String message) {
        super(message);
    }
}
