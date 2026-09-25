package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.InsurancePlan;
import com.koustubh.bank.domain.InsurancePolicy;
import com.koustubh.bank.domain.InsuranceRequest;
import com.koustubh.bank.domain.InsuranceRequestStatus;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class InsuranceServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired InsuranceService insurance;
    @Autowired NotificationService notifications;
    @Autowired CustomerService customers;
    @Autowired Clock clock;

    private InsuranceService.Request health(String mobile) {
        return new InsuranceService.Request(InsurancePlan.HEALTH, 1_000_000L, "Test Customer", mobile, "Bhopal", 30,
                "Self, spouse", InsuranceService.CALL_TIMES.get(1));
    }

    @Test
    void allFormProblemsAreReportedTogether() {
        OpenedAccount a = accounts.active(0);
        InsuranceService.Request bad = new InsuranceService.Request(InsurancePlan.HEALTH, 123L, "", "12345", "", 10, "", "Midnight");
        assertThatThrownBy(() -> insurance.request(a.customerId(), bad))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cover amount").hasMessageContaining("name").hasMessageContaining("mobile")
                .hasMessageContaining("city").hasMessageContaining("Age").hasMessageContaining("time");
    }

    @Test
    void requestIsCalledBackThenPolicyIssuedWithPremiumDebit() {
        OpenedAccount a = accounts.active(20_000);
        InsuranceRequest r = insurance.request(a.customerId(), health("9876543210"));
        assertThat(r.getStatus()).isEqualTo(InsuranceRequestStatus.REQUESTED);
        assertThat(r.getReference()).startsWith("JBQ");
        assertThat(notifications.recentFor(customers.overview(a.customerId()).customer()).get(0).getMessage())
                .contains("within 24 hours");

        insurance.markContacted(r.getId(), "neha.officer", "Shared 3 quotes");
        InsurancePolicy p = insurance.issue(r.getId(), "neha.officer", new InsuranceService.Issue(
                "Demo General Insurance", "DGI/HL/0001", 1_000_000L, new BigDecimal("12500"), LocalDate.now(clock)));

        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("7500");
        assertThat(insurance.requestsOf(a.customerId()).get(0).getStatus()).isEqualTo(InsuranceRequestStatus.POLICY_ISSUED);
        assertThat(insurance.policyOf(a.customerId(), p.getId()).getInsurer()).isEqualTo("Demo General Insurance");
        assertThatThrownBy(() -> insurance.markContacted(r.getId(), "x", "again")).hasMessageContaining("already");
        // Another customer can't open this policy
        OpenedAccount other = accounts.active(0);
        assertThatThrownBy(() -> insurance.policyOf(other.customerId(), p.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void closingNeedsAReason() {
        OpenedAccount a = accounts.active(0);
        InsuranceRequest r = insurance.request(a.customerId(), health("9876543211"));
        assertThatThrownBy(() -> insurance.close(r.getId(), "admin", " ")).isInstanceOf(InvalidRequestException.class);
        insurance.close(r.getId(), "admin", "Customer not interested");
        assertThat(insurance.openRequests()).noneMatch(x -> x.getId().equals(r.getId()));
    }
}
