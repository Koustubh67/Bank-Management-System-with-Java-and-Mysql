package com.koustubh.bank.exception;

/** Wrong UPI PIN during a payment or balance check. The failed attempt is still saved. */
public class WrongUpiPinException extends BankException {
    public WrongUpiPinException(String message) {
        super(message);
    }
}
