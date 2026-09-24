package com.koustubh.bank.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmiCalculatorTest {

    @Test
    void matchesTheTextbookEmi() {
        // ₹1,00,000 at 10% for 12 months: EMI ₹8,791.59 (standard reducing-balance formula)
        assertThat(EmiCalculator.emi(new BigDecimal("100000"), new BigDecimal("10"), 12)).isEqualByComparingTo("8791.59");
        // Zero interest: principal split equally
        assertThat(EmiCalculator.emi(new BigDecimal("12000"), BigDecimal.ZERO, 12)).isEqualByComparingTo("1000.00");
    }

    @ParameterizedTest
    @CsvSource({"100000, 10, 12", "600000, 8.75, 60", "4500000, 8.50, 240", "30000, 9.50, 12", "2500000, 10.99, 60",
            "10000, 9.00, 6", "5000000, 8.50, 360"})
    void scheduleRepaysThePrincipalExactlyAndEndsAtZero(BigDecimal principal, BigDecimal rate, int months) {
        LocalDate first = LocalDate.of(2026, 1, 28);
        List<EmiCalculator.Row> rows = EmiCalculator.schedule(principal, rate, months, first);

        assertThat(rows).hasSize(months);
        assertThat(rows.stream().map(EmiCalculator.Row::principal).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(principal);
        assertThat(rows.get(months - 1).balance()).isEqualByComparingTo("0");
        assertThat(rows.get(months - 1).dueDate()).isEqualTo(first.plusMonths(months - 1));
        BigDecimal emi = rows.get(0).emi();
        for (int i = 0; i < months; i++) {
            EmiCalculator.Row r = rows.get(i);
            assertThat(r.principal().add(r.interest())).isEqualByComparingTo(r.emi());
            if (i < months - 1) {
                assertThat(r.emi()).isEqualByComparingTo(emi);          // every EMI is the same except the last
            }
            if (i > 0) {
                assertThat(r.interest()).isLessThanOrEqualTo(rows.get(i - 1).interest()); // reducing balance
            }
        }
        // The EMI is rounded to the paisa, so up to half a paisa a month is over- or under-paid, and that
        // difference grows with interest. The last EMI absorbs it: at most 0.005 × ((1 + r)^n − 1) / r rupees.
        double r = rate.doubleValue() / 1200;
        double maxDrift = 0.005 * (Math.pow(1 + r, months) - 1) / r + 0.01;
        assertThat(rows.get(months - 1).emi().subtract(emi).abs().doubleValue()).isLessThanOrEqualTo(maxDrift);
    }

    @Test
    void firstEmiIsOneMonthLaterOnTheSameDayButNeverAfterThe28th() {
        assertThat(EmiCalculator.firstEmiDate(LocalDate.of(2026, 3, 15))).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(EmiCalculator.firstEmiDate(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(EmiCalculator.firstEmiDate(LocalDate.of(2026, 5, 30))).isEqualTo(LocalDate.of(2026, 6, 28));
    }
}
