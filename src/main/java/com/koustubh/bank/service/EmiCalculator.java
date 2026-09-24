package com.koustubh.bank.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Standard reducing-balance EMI maths, the way Indian banks calculate it:
 * EMI = P × r × (1 + r)^n / ((1 + r)^n − 1), with r = annual rate / 12 / 100.
 * Each month's interest is charged on the balance still owed; the rest of the EMI repays principal.
 * The last EMI is adjusted so the principal repaid adds up to the loan exactly, to the paisa.
 */
public final class EmiCalculator {

    public record Row(int number, LocalDate dueDate, BigDecimal emi, BigDecimal principal, BigDecimal interest,
                      BigDecimal balance) {
    }

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private EmiCalculator() {
    }

    public static BigDecimal emi(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (months <= 0 || principal.signum() <= 0) {
            throw new IllegalArgumentException("Principal and tenure must be positive");
        }
        if (annualRatePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal pow = BigDecimal.ONE.add(r).pow(months, MC);
        return principal.multiply(r, MC).multiply(pow, MC).divide(pow.subtract(BigDecimal.ONE), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** The full repayment schedule; instalment n is due {@code n - 1} months after {@code firstDue}. */
    public static List<Row> schedule(BigDecimal principal, BigDecimal annualRatePercent, int months, LocalDate firstDue) {
        BigDecimal emi = emi(principal, annualRatePercent, months);
        BigDecimal r = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal balance = principal.setScale(2, RoundingMode.HALF_UP);
        List<Row> rows = new ArrayList<>(months);
        for (int n = 1; n <= months; n++) {
            BigDecimal interest = balance.multiply(r, MC).setScale(2, RoundingMode.HALF_UP);
            BigDecimal principalPart = emi.subtract(interest);
            if (n == months || principalPart.compareTo(balance) > 0) {
                principalPart = balance; // last EMI clears the balance exactly
            }
            balance = balance.subtract(principalPart);
            rows.add(new Row(n, firstDue.plusMonths(n - 1L), principalPart.add(interest), principalPart, interest, balance));
        }
        return rows;
    }

    public static BigDecimal totalInterest(List<Row> schedule) {
        return schedule.stream().map(Row::interest).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** First EMI one month after disbursal, on the same day of the month (the 29th–31st become the 28th). */
    public static LocalDate firstEmiDate(LocalDate disbursedOn) {
        LocalDate next = disbursedOn.plusMonths(1);
        return next.getDayOfMonth() > 28 ? next.withDayOfMonth(28) : next;
    }
}
