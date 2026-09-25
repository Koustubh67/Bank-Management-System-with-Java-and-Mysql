package com.koustubh.bank.web;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    /** Amount in words the Indian way, as printed on receipts: "Rupees Twelve Thousand ... and Thirty Four Paise Only". */
    public String words(BigDecimal amount) {
        BigDecimal a = amount.setScale(2, RoundingMode.HALF_UP);
        long rupees = a.longValue();
        int paise = a.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        return "Rupees " + (rupees == 0 ? "Zero" : indian(rupees)) + (paise > 0 ? " and " + upTo99(paise) + " Paise" : "") + " Only";
    }

    /** Groups as crore, lakh, thousand, hundred (e.g. 1,23,45,678). */
    private static String indian(long n) {
        StringBuilder out = new StringBuilder();
        if (n >= 10_000_000) {
            out.append(indian(n / 10_000_000)).append(" Crore ");
            n %= 10_000_000;
        }
        if (n >= 100_000) {
            out.append(upTo99((int) (n / 100_000))).append(" Lakh ");
            n %= 100_000;
        }
        if (n >= 1000) {
            out.append(upTo99((int) (n / 1000))).append(" Thousand ");
            n %= 1000;
        }
        if (n >= 100) {
            out.append(ONES[(int) (n / 100)]).append(" Hundred ");
            n %= 100;
        }
        if (n > 0) {
            out.append(upTo99((int) n));
        }
        return out.toString().trim();
    }

    private static String upTo99(int n) {
        return n < 20 ? ONES[n] : TENS[n / 10] + (n % 10 > 0 ? " " + ONES[n % 10] : "");
    }

    public String format(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(INDIA);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
