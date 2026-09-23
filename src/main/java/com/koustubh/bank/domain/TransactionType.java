package com.koustubh.bank.domain;

public enum TransactionType {
    DEPOSIT("Deposit", true),
    WITHDRAWAL("Withdrawal", false),
    TRANSFER_IN("Transfer In", true),
    TRANSFER_OUT("Transfer Out", false);

    private final String label;
    private final boolean credit;

    TransactionType(String label, boolean credit) {
        this.label = label;
        this.credit = credit;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCredit() {
        return credit;
    }
}
