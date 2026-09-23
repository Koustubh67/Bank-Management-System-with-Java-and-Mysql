package com.koustubh.bank.exception;

public class InsufficientFundsException extends BankException {
    public InsufficientFundsException() {
        super("Insufficient balance");
    }
}
