package com.koustubh.bank.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** Formats rupee amounts with Indian digit grouping, e.g. ₹1,25,000.00. Used in templates as ${@money.format(x)}. */
@Component("money")
public class Money {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    public String format(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
