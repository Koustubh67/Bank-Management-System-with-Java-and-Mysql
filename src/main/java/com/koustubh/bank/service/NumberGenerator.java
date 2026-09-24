package com.koustubh.bank.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates account numbers, card numbers and PINs. Uses SecureRandom because these are security-sensitive. */
@Component
public class NumberGenerator {

    /** Issuer identification number printed at the start of every card. */
    static final String CARD_PREFIX = "504093";

    private final SecureRandom random;

    public NumberGenerator(SecureRandom random) {
        this.random = random;
    }

    public String newAccountNumber() {
        return "10" + randomDigits(10);
    }

    /** 16 digits: 6-digit prefix + 9 random digits + Luhn check digit. */
    public String newCardNumber() {
        String partial = CARD_PREFIX + randomDigits(9);
        return partial + luhnCheckDigit(partial);
    }

    /** Net banking Customer ID, e.g. JB48213377. */
    public String newCustomerId() {
        return "JB" + randomDigits(8);
    }

    /** Reference numbers for deposits, SIPs and policies, e.g. JBFD4821337712. */
    public String newReference(String prefix) {
        return prefix + randomDigits(10);
    }

    public String newPin() {
        return randomDigits(4);
    }

    private String randomDigits(int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** The digit that makes {@code partial + digit} pass the Luhn check used by real card networks. */
    static int luhnCheckDigit(String partial) {
        int sum = 0;
        boolean doubleIt = true;
        for (int i = partial.length() - 1; i >= 0; i--) {
            int d = partial.charAt(i) - '0';
            if (doubleIt) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            doubleIt = !doubleIt;
        }
        return (10 - sum % 10) % 10;
    }

    static boolean isValidLuhn(String number) {
        return luhnCheckDigit(number.substring(0, number.length() - 1)) == number.charAt(number.length() - 1) - '0';
    }
}
