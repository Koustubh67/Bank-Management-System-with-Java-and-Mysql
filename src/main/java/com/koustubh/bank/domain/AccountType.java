package com.koustubh.bank.domain;

public enum AccountType {
    SAVINGS("Savings Account"),
    CURRENT("Current Account"),
    FIXED_DEPOSIT("Fixed Deposit Account"),
    RECURRING_DEPOSIT("Recurring Deposit Account");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
