package com.koustubh.bank.exception;

/** The customer entered something invalid, e.g. an amount that is not a multiple of 100. */
public class InvalidRequestException extends BankException {
    public InvalidRequestException(String message) {
        super(message);
    }
}
