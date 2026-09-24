package com.koustubh.bank.domain;

import java.math.BigDecimal;

/**
 * Loan products and their pricing. Like the repo-linked (EBLR) loans Indian banks offer, a floating rate is the RBI
 * repo rate plus a spread, and a fixed rate adds a premium on top because the bank carries the risk of rates rising.
 * With the repo rate at 5.25% a floating home loan is 8.50% and a fixed one 9.50%. Limits are demo values in the
 * range Indian banks use.
 */
public enum LoanType {
    HOME("Home loan", "Buy, build or renovate your home", "3.25", "1.00", 500_000, 50_000_000, 60, 360, "🏠"),
    CAR("Car loan", "New or used car, up to 100% on-road funding", "3.50", "0.50", 100_000, 5_000_000, 12, 84, "🚗"),
    PERSONAL("Personal loan", "Weddings, travel, medical or anything else", "5.74", "0.50", 50_000, 2_500_000, 12, 60, "💼"),
    EDUCATION("Education loan", "Study in India or abroad", "4.00", "0.75", 100_000, 5_000_000, 12, 120, "🎓"),
    TWO_WHEELER("Two-wheeler loan", "Bikes and scooters, quick approval", "4.25", "0.50", 30_000, 300_000, 12, 48, "🏍"),
    GOLD("Gold loan", "Against your gold jewellery, same-day money", "3.75", "0.25", 10_000, 2_500_000, 6, 36, "🪙");

    private final String label;
    private final String tagline;
    private final BigDecimal spread;
    private final BigDecimal fixedPremium;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final int minMonths;
    private final int maxMonths;
    private final String icon;

    LoanType(String label, String tagline, String spread, String fixedPremium, long minAmount, long maxAmount,
             int minMonths, int maxMonths, String icon) {
        this.label = label;
        this.tagline = tagline;
        this.spread = new BigDecimal(spread);
        this.fixedPremium = new BigDecimal(fixedPremium);
        this.minAmount = BigDecimal.valueOf(minAmount);
        this.maxAmount = BigDecimal.valueOf(maxAmount);
        this.minMonths = minMonths;
        this.maxMonths = maxMonths;
        this.icon = icon;
    }

    public String getLabel() { return label; }
    public String getTagline() { return tagline; }
    /** Added to the repo rate to give the floating rate. */
    public BigDecimal getSpread() { return spread; }
    /** Added to the floating rate to give the fixed rate. */
    public BigDecimal getFixedPremium() { return fixedPremium; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public int getMinMonths() { return minMonths; }
    public int getMaxMonths() { return maxMonths; }
    public String getIcon() { return icon; }
}
