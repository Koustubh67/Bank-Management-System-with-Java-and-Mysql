package com.koustubh.bank.domain;

import java.math.BigDecimal;

/** Demo mutual funds offered for SIPs. Expected returns are illustrative, not promises. */
public enum Fund {
    NIFTY_INDEX("JavaBank Nifty 50 Index Fund", "Equity · Index", "12.00", "Moderately high"),
    FLEXI_CAP("JavaBank Flexi Cap Fund", "Equity · Flexi cap", "14.00", "Very high"),
    LIQUID("JavaBank Liquid Fund", "Debt · Liquid", "6.50", "Low");

    private final String label;
    private final String category;
    private final BigDecimal expectedReturn;
    private final String risk;

    Fund(String label, String category, String expectedReturn, String risk) {
        this.label = label;
        this.category = category;
        this.expectedReturn = new BigDecimal(expectedReturn);
        this.risk = risk;
    }

    public String getLabel() { return label; }
    public String getCategory() { return category; }
    public BigDecimal getExpectedReturn() { return expectedReturn; }
    public String getRisk() { return risk; }
}
