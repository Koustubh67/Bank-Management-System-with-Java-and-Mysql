package com.koustubh.bank.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Formats rupee amounts with Indian digit grouping, e.g. ₹1,25,000.00. Used in templates as ${@money.format(x)}. */
@Component("money")
public class Money {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    /** Short Indian style for cover amounts: ₹5 lakh, ₹1 crore, ₹2.5 crore. */
    public String cover(Number amount) {
        double v = amount.doubleValue();
        if (v >= 10_000_000) {
            return "₹" + trim(v / 10_000_000) + " crore";
        }
        if (v >= 100_000) {
            return "₹" + trim(v / 100_000) + " lakh";
        }
        return format(BigDecimal.valueOf(v)).replace(".00", "");
    }

    /** Whole rupees, e.g. ₹12,500. */
    public String whole(BigDecimal amount) {
        return format(amount).replaceAll("\\.\\d{2}$", "");
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10) / 10.0);
    }

    public String format(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
