package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InsufficientFundsException;
import com.koustubh.bank.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WealthServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired WealthService wealth;
    @Autowired CustomerService customers;

    @Test
    void fdMaturityIsCompoundedQuarterly() {
        // 1,00,000 at 7% for 2 years: 100000 × (1.0175)^8 = 1,14,888.18
        assertThat(WealthService.maturityAmount(new BigDecimal("100000"), FdTenure.M24)).isEqualByComparingTo("114888.18");
    }

    @Test
    void openingAnFdDebitsTheAccountAndShowsInThePortfolio() {
        OpenedAccount a = accounts.active(60_000);
        Investment fd = wealth.openFixedDeposit(a.customerId(), new BigDecimal("50000"), FdTenure.M12);

        assertThat(fd.getReference()).startsWith("JBFD");
        assertThat(fd.getMaturityDate()).isEqualTo(fd.getStartDate().plusMonths(12));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("10000");
        Transaction debit = customers.overview(a.customerId()).recent().get(0);
        assertThat(debit.getType()).isEqualTo(TransactionType.FD_BOOKING);
        assertThat(debit.getRemarks()).startsWith("FD/" + fd.getReference());

        WealthService.Portfolio p = wealth.portfolio(a.customerId());
        assertThat(p.investments()).hasSize(1);
        assertThat(p.invested()).isEqualByComparingTo("50000");
        assertThat(p.fdMaturityValue()).isEqualByComparingTo(fd.getMaturityAmount());
    }

    @Test
    void fdAndSipRulesAreChecked() {
        OpenedAccount a = accounts.active(10_000);
        assertThatThrownBy(() -> wealth.openFixedDeposit(a.customerId(), new BigDecimal("4999"), FdTenure.M12))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> wealth.openFixedDeposit(a.customerId(), new BigDecimal("20000"), FdTenure.M12))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> wealth.startSip(a.customerId(), Fund.LIQUID, new BigDecimal("450")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> wealth.startSip(a.customerId(), Fund.LIQUID, new BigDecimal("1050")))
                .hasMessageContaining("multiple of Rs 100");
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("10000");

        Investment sip = wealth.startSip(a.customerId(), Fund.FLEXI_CAP, new BigDecimal("2500"));
        assertThat(sip.getNextDebitDate()).isEqualTo(sip.getStartDate().plusMonths(1));
        assertThat(sip.getInvested()).isEqualByComparingTo("2500");
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("7500");
    }

    @Test
    void pendingAccountsCannotInvest() {
        OpenedAccount a = accounts.pending();
        assertThatThrownBy(() -> wealth.startSip(a.customerId(), Fund.LIQUID, new BigDecimal("500")))
                .hasMessageContaining("approval");
    }

    @Test
    void premiumsDependOnPlanCoverAndAge() {
        assertThat(WealthService.premium(InsurancePlan.TERM_LIFE, 10_000_000, 25)).isEqualByComparingTo("6000");
        assertThat(WealthService.premium(InsurancePlan.TERM_LIFE, 10_000_000, 40)).isEqualByComparingTo("15000");
        assertThat(WealthService.premium(InsurancePlan.MOTOR, 300_000, 30)).isEqualByComparingTo("8400");
        assertThat(WealthService.premium(InsurancePlan.TRAVEL, 1_000_000, 60)).isEqualByComparingTo("1499");
    }

    @Test
    void buyingAPolicyDebitsThePremium() {
        OpenedAccount a = accounts.active(20_000);
        assertThatThrownBy(() -> wealth.buyPolicy(a.customerId(), InsurancePlan.TERM_LIFE, 123L, "Mother"))
                .hasMessageContaining("cover amount");
        assertThatThrownBy(() -> wealth.buyPolicy(a.customerId(), InsurancePlan.HEALTH, 500_000L, " "))
                .hasMessageContaining("nominee name");

        InsurancePolicy p = wealth.buyPolicy(a.customerId(), InsurancePlan.MOTOR, 300_000L, "MP04 AB 1234");
        assertThat(p.getPolicyNumber()).startsWith("JBI");
        assertThat(p.getEndDate()).isEqualTo(p.getStartDate().plusYears(1).minusDays(1));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("11600");
        assertThat(wealth.quotes(a.customerId()).get(InsurancePlan.MOTOR)).containsEntry(300_000L, new BigDecimal("8400.00"));
    }
}
