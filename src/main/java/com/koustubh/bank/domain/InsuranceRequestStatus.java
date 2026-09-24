package com.koustubh.bank.domain;

public enum InsuranceRequestStatus {
    REQUESTED("Request received", "Our expert will call you within 24 hours"),
    CONTACTED("Expert contacted you", "We're preparing your policy with the insurer"),
    POLICY_ISSUED("Policy issued", "Your policy is active"),
    CLOSED("Closed", "This request was closed");

    private final String label;
    private final String hint;

    InsuranceRequestStatus(String label, String hint) {
        this.label = label;
        this.hint = hint;
    }

    public String getLabel() { return label; }
    public String getHint() { return hint; }
}
