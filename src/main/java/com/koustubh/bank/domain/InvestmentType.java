package com.koustubh.bank.domain;

public enum InvestmentType {
    FIXED_DEPOSIT("Fixed Deposit"),
    SIP("Mutual Fund SIP"),
    LUMPSUM("Mutual Fund · One-time");

    private final String label;

    InvestmentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public boolean isMutualFund() {
        return this != FIXED_DEPOSIT;
    }
}
