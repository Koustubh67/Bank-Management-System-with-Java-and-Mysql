package com.koustubh.bank.domain;

public enum TransactionType {
    DEPOSIT("Deposit", true),
    WITHDRAWAL("Withdrawal", false),
    TRANSFER_IN("Transfer In", true),
    TRANSFER_OUT("Transfer Out", false),
    UPI_IN("UPI Received", true),
    UPI_OUT("UPI Payment", false),
    FD_BOOKING("Fixed Deposit", false),
    SIP_INSTALMENT("SIP Instalment", false),
    MF_PURCHASE("Mutual Fund Purchase", false),
    INSURANCE_PREMIUM("Insurance Premium", false);

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
