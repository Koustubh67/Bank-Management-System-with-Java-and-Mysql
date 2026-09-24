package com.koustubh.bank.domain;

public enum InvestmentType {
    FIXED_DEPOSIT("Fixed Deposit"),
    SIP("Mutual Fund SIP");

    private final String label;

    InvestmentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
