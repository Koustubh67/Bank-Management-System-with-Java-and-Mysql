package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.FdTenure;
import com.koustubh.bank.domain.Investment;
import com.koustubh.bank.domain.InvestmentType;
import com.koustubh.bank.domain.PaymentMethod;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.service.ChartService.Point;
import com.koustubh.bank.service.ChartService.Range;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Test NAVs (FakeNavSource) rise by 0.01 a day and are 100.00 today. */
@SpringBootTest
@ActiveProfiles("test")
class ChartServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired ChartService charts;
    @Autowired InvestmentService investments;
    @Autowired Clock clock;

    @Test
    void fundChartIsOldestFirstAndEndsAtTodaysNav() {
        List<Point> year = charts.fund(122639, Range.Y1);
        assertThat(year.size()).isBetween(100, 200);
        assertThat(year.get(0).date()).isEqualTo(LocalDate.now(clock).minusYears(1).toString());
        assertThat(year.get(year.size() - 1).value()).isEqualByComparingTo("100.00");
        assertThat(year.get(0).value()).isLessThan(year.get(year.size() - 1).value());
        assertThat(charts.fund(122639, Range.M1).size()).isLessThan(year.size());
        assertThat(charts.fund(122639, Range.ALL).get(0).date()).isLessThan(year.get(0).date());
    }

    @Test
    void sipChartStepsUpWithEachInstalmentAndEndsAtTheHoldingValue() {
        OpenedAccount a = accounts.active(0);
        Investment sip = investments.importExistingSip(a.customerId(), 122639, BigDecimal.valueOf(1_000), 6);
        List<Point> points = charts.holding(a.customerId(), sip.getId(), Range.ALL);

        assertThat(points.get(0).date()).isEqualTo(sip.getStartDate().toString());
        assertThat(points.get(0).invested()).isEqualByComparingTo("1000");
        Point last = points.get(points.size() - 1);
        assertThat(last.invested()).isEqualByComparingTo("6000");
        assertThat(last.value().doubleValue())
                .isCloseTo(investments.holding(a.customerId(), sip.getId()).currentValue().doubleValue(), within(0.5));
        // invested never goes down
        for (int i = 1; i < points.size(); i++) {
            assertThat(points.get(i).invested()).isGreaterThanOrEqualTo(points.get(i - 1).invested());
        }
    }

    @Test
    void portfolioChartAddsFundsAndFds() {
        OpenedAccount a = accounts.active(20_000);
        investments.pay(a.customerId(), new InvestmentService.Order(InvestmentType.FIXED_DEPOSIT, null, BigDecimal.valueOf(10_000), FdTenure.M12), PaymentMethod.ACCOUNT);
        investments.pay(a.customerId(), new InvestmentService.Order(InvestmentType.LUMPSUM, 120716L, BigDecimal.valueOf(5_000), null), PaymentMethod.ACCOUNT);
        List<Point> points = charts.portfolio(a.customerId(), Range.Y1);
        Point last = points.get(points.size() - 1);
        assertThat(last.invested()).isEqualByComparingTo("15000");
        assertThat(last.value().doubleValue()).isCloseTo(investments.portfolio(a.customerId()).currentValue().doubleValue(), within(0.5));
    }

    @Test
    void customersOnlySeeTheirOwnHoldingCharts() {
        OpenedAccount a = accounts.active(0);
        OpenedAccount b = accounts.active(0);
        Investment sip = investments.importExistingSip(a.customerId(), 122639, BigDecimal.valueOf(1_000), 2);
        assertThatThrownBy(() -> charts.holding(b.customerId(), sip.getId(), Range.Y1)).isInstanceOf(NotFoundException.class);
        assertThat(charts.portfolio(b.customerId(), Range.Y1)).isEmpty();
    }

    @Test
    void unknownRangeIsRejected() {
        assertThatThrownBy(() -> Range.parse("10Y")).hasMessageContaining("range");
        assertThat(Range.parse("m6")).isEqualTo(Range.M6);
    }
}
