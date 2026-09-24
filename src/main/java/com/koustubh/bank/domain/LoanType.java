package com.koustubh.bank.domain;

import java.math.BigDecimal;

/** Loan products with indicative interest rates and limits (demo values, similar to Indian bank ranges). */
public enum LoanType {
    HOME("Home loan", "Buy, build or renovate your home", "8.50", 500_000, 50_000_000, 60, 360, "🏠"),
    CAR("Car loan", "New or used car, up to 100% on-road funding", "8.75", 100_000, 5_000_000, 12, 84, "🚗"),
    PERSONAL("Personal loan", "Weddings, travel, medical or anything else", "10.99", 50_000, 2_500_000, 12, 60, "💼"),
    EDUCATION("Education loan", "Study in India or abroad", "9.25", 100_000, 5_000_000, 12, 120, "🎓"),
    TWO_WHEELER("Two-wheeler loan", "Bikes and scooters, quick approval", "9.50", 30_000, 300_000, 12, 48, "🏍"),
    GOLD("Gold loan", "Against your gold jewellery, same-day money", "9.00", 10_000, 2_500_000, 6, 36, "🪙");

    private final String label;
    private final String tagline;
    private final BigDecimal rate;
    private final BigDecimal minAmount;
    private final BigDecimal maxAmount;
    private final int minMonths;
    private final int maxMonths;
    private final String icon;

    LoanType(String label, String tagline, String rate, long minAmount, long maxAmount, int minMonths, int maxMonths,
             String icon) {
        this.label = label;
        this.tagline = tagline;
        this.rate = new BigDecimal(rate);
        this.minAmount = BigDecimal.valueOf(minAmount);
        this.maxAmount = BigDecimal.valueOf(maxAmount);
        this.minMonths = minMonths;
        this.maxMonths = maxMonths;
        this.icon = icon;
    }

    public String getLabel() { return label; }
    public String getTagline() { return tagline; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getMinAmount() { return minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public int getMinMonths() { return minMonths; }
    public int getMaxMonths() { return maxMonths; }
    public String getIcon() { return icon; }
}
