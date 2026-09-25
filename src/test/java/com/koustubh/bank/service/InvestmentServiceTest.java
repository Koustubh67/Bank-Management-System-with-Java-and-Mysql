package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InsufficientFundsException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.market.FakeNavSource;
import com.koustubh.bank.service.InvestmentService.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Uses FakeNavSource: every fund's NAV is 100.00 today and 0.01 lower for each day back. */
@SpringBootTest
@ActiveProfiles("test")
class InvestmentServiceTest {

    static final long PPFAS = 122639;

    @Autowired TestAccounts accounts;
    @Autowired InvestmentService investments;
    @Autowired CustomerService customers;
    @Autowired Clock clock;

    private static BigDecimal rs(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    void fdMaturityIsCompoundedQuarterly() {
        // 1,00,000 at 7% for 2 years: 100000 × (1.0175)^8 = 1,14,888.18
        assertThat(InvestmentService.maturityAmount(rs(100_000), FdTenure.M24)).isEqualByComparingTo("114888.18");
    }

    @Test
    void lumpsumBuysUnitsAtTodaysNavAndIsDebited() {
        OpenedAccount a = accounts.active(20_000);
        Investment inv = investments.pay(a.customerId(), new Order(InvestmentType.LUMPSUM, PPFAS, rs(5_000), null), PaymentMethod.ACCOUNT);

        assertThat(inv.getUnits()).isEqualByComparingTo("50.0000");      // 5000 / 100.00
        assertThat(inv.getReference()).startsWith("JBMF");
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("15000");
        Transaction debit = customers.overview(a.customerId()).recent().get(0);
        assertThat(debit.getType()).isEqualTo(TransactionType.MF_PURCHASE);

        InvestmentService.Portfolio p = investments.portfolio(a.customerId());
        assertThat(p.currentValue()).isEqualByComparingTo("5000.00");
        assertThat(p.gain()).isEqualByComparingTo("0");
    }

    @Test
    void aYearOldSipShowsMarketProfit() {
        OpenedAccount a = accounts.active(0);
        Investment sip = investments.importExistingSip(a.customerId(), PPFAS, rs(1_000), 12);
        assertThat(sip.getInstalmentsPaid()).isEqualTo(12);
        assertThat(sip.getInvested()).isEqualByComparingTo("12000");
        assertThat(sip.getNextDebitDate()).isEqualTo(LocalDate.now(clock).plusMonths(1));

        InvestmentService.Holding h = investments.holding(a.customerId(), sip.getId());
        assertThat(h.priced()).isTrue();
        assertThat(h.currentValue()).isGreaterThan(rs(12_000)); // bought at lower NAVs than today's
        assertThat(h.gainPercent()).isPositive();
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("0"); // demo import doesn't debit
    }

    @Test
    void dueSipInstalmentsAreDebitedAndMissedOnesSkipped() {
        OpenedAccount a = accounts.active(1_500);
        Investment sip = investments.importExistingSip(a.customerId(), PPFAS, rs(1_000), 1);
        // Pretend two months have passed: move the next debit date back
        Investment stored = sip;
        stored.skipInstalment();
        org.springframework.test.util.ReflectionTestUtils.setField(stored, "nextDebitDate", LocalDate.now(clock).minusMonths(1));
        investments.saveForTest(stored);

        int paid = investments.processDueSips();
        assertThat(paid).isGreaterThanOrEqualTo(1);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("500"); // one instalment paid, next one missed (low balance)
        Investment after = investments.holding(a.customerId(), sip.getId()).investment();
        assertThat(after.getInstalmentsPaid()).isEqualTo(2);
        assertThat(after.getNextDebitDate()).isAfter(LocalDate.now(clock));
    }

    @Test
    void ordersAreValidated() {
        assertThatThrownBy(() -> investments.validate(new Order(InvestmentType.SIP, PPFAS, rs(450), null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> investments.validate(new Order(InvestmentType.SIP, PPFAS, rs(1050), null)))
                .hasMessageContaining("multiple of Rs 100");
        assertThatThrownBy(() -> investments.validate(new Order(InvestmentType.SIP, 999L, rs(1000), null)))
                .hasMessageContaining("choose a fund");
        assertThatThrownBy(() -> investments.validate(new Order(InvestmentType.FIXED_DEPOSIT, null, rs(4_999), FdTenure.M12)))
                .hasMessageContaining("5,000");
        OpenedAccount poor = accounts.active(1_000);
        assertThatThrownBy(() -> investments.pay(poor.customerId(), new Order(InvestmentType.FIXED_DEPOSIT, null, rs(5_000), FdTenure.M12), PaymentMethod.ACCOUNT))
                .isInstanceOf(InsufficientFundsException.class);
        OpenedAccount pending = accounts.pending();
        assertThatThrownBy(() -> investments.pay(pending.customerId(), new Order(InvestmentType.SIP, PPFAS, rs(500), null), PaymentMethod.ACCOUNT))
                .hasMessageContaining("approval");
    }

    @Test
    void fdAccruesInterestOverTime() {
        OpenedAccount a = accounts.active(50_000);
        Investment fd = investments.pay(a.customerId(), new Order(InvestmentType.FIXED_DEPOSIT, null, rs(50_000), FdTenure.M12), PaymentMethod.UPI);
        assertThat(fd.getMaturityDate()).isEqualTo(LocalDate.now(clock).plusMonths(12));
        assertThat(InvestmentService.accruedValue(fd, LocalDate.now(clock).plusMonths(6))).isBetween(rs(51_600), rs(51_800));
        assertThat(InvestmentService.accruedValue(fd, LocalDate.now(clock).plusYears(5))).isEqualByComparingTo(
                InvestmentService.accruedValue(fd, fd.getMaturityDate()));
    }
}
