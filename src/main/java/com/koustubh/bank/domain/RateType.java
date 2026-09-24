package com.koustubh.bank.domain;

/** How a loan's interest rate behaves over its life. The customer chooses when applying. */
public enum RateType {
    FLOATING("Floating", "Linked to the RBI repo rate. Starts lower; your EMI goes up or down when the repo rate changes"),
    FIXED("Fixed", "Same rate and EMI for the whole loan, whatever happens to market rates");

    private final String label;
    private final String description;

    RateType(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
