package com.koustubh.bank.domain;

import java.math.BigDecimal;

/** Fixed deposit tenures and their (illustrative) interest rates per year. */
public enum FdTenure {
    M12(12, "6.80"),
    M24(24, "7.00"),
    M36(36, "7.10"),
    M60(60, "7.25");

    private final int months;
    private final BigDecimal rate;

    FdTenure(int months, String rate) {
        this.months = months;
        this.rate = new BigDecimal(rate);
    }

    public int getMonths() { return months; }
    public BigDecimal getRate() { return rate; }

    public String getLabel() {
        return months / 12 + (months == 12 ? " year" : " years");
    }
}
