package com.koustubh.bank.domain;

/** How a customer pays for an investment at checkout. Both move money through the bank's own ledger. */
public enum PaymentMethod {
    ACCOUNT("JavaBank savings account", "OTP sent to your mobile"),
    UPI("JavaPay UPI", "Confirm with your UPI PIN");

    private final String label;
    private final String hint;

    PaymentMethod(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String getLabel() { return label; }
    public String getHint() { return hint; }
}
