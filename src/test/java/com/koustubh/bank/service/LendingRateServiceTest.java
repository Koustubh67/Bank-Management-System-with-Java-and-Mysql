package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.*;
import com.koustubh.bank.repository.LoanInstalmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fixed and floating rates, and what happens to loans when the repo rate changes. */
@SpringBootTest
@ActiveProfiles("test")
class LendingRateServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired LoanService loans;
    @Autowired LendingRateService rates;
    @Autowired LoanInstalmentRepository instalments;
    @Autowired CustomerService customers;
    @Autowired NotificationService notifications;
    @Autowired PlatformTransactionManager txManager;
    @Autowired Clock clock;

    private BigDecimal originalRepo;

    @BeforeEach
    void rememberRepoRate() {
        originalRepo = rates.current().getRatePercent();
    }

    /** The repo rate is shared by every test: put it back (repricing any floating loans again). */
    @AfterEach
    void restoreRepoRate() {
        if (rates.current().getRatePercent().compareTo(originalRepo) != 0) {
            rates.changeRepoRate(originalRepo, "Restored after test", "test");
        }
    }

    private static BigDecimal rs(long v) {
        return BigDecimal.valueOf(v);
    }

    private Loan approvedLoan(OpenedAccount a, LoanType type, RateType rateType, long amount, int months) {
        Loan applied = loans.apply(a.customerId(), new LoanService.Application(type, rateType, rs(amount), months,
                "Test", "Salaried", rs(500_000)));
        return loans.approve(applied.getId(), "neha", rs(amount), rates.rate(type, rateType), months);
    }

    @Test
    void floatingIsRepoPlusSpreadAndFixedCostsAPremium() {
        BigDecimal repo = rates.current().getRatePercent();
        for (LendingRateService.Offer o : rates.offers()) {
            assertThat(o.floating()).isEqualByComparingTo(repo.add(o.type().getSpread()));
            assertThat(o.fixed()).isEqualByComparingTo(o.floating().add(o.type().getFixedPremium()));
            assertThat(o.fixed()).isGreaterThan(o.floating());
        }
    }

    @Test
    void aRepoChangeRepricesOnlyTheFutureEmisOfFloatingLoans() {
        OpenedAccount a = accounts.active(100_000);
        Loan floating = approvedLoan(a, LoanType.CAR, RateType.FLOATING, 500_000, 60);
        Loan fixed = approvedLoan(a, LoanType.PERSONAL, RateType.FIXED, 200_000, 24);
        // Two EMIs paid early, and EMI 3 overdue (due yesterday, not paid)
        loans.payNext(a.customerId(), floating.getId());
        loans.payNext(a.customerId(), floating.getId());
        List<LoanInstalment> before = loans.loanOf(a.customerId(), floating.getId()).schedule();
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            LoanInstalment third = instalments.findById(before.get(2).getId()).orElseThrow();
            ReflectionTestUtils.setField(third, "dueDate", LocalDate.now(clock).minusDays(1));
        });
        List<LoanInstalment> fixedBefore = loans.loanOf(a.customerId(), fixed.getId()).schedule();
        BigDecimal oldRate = floating.getRatePercent();
        BigDecimal newRepo = originalRepo.add(new BigDecimal("0.50"));

        LendingRateService.RepoChange result = rates.changeRepoRate(newRepo, "RBI policy: +0.50%", "admin");

        assertThat(result.repriced()).isGreaterThanOrEqualTo(1);
        assertThat(rates.current().getRatePercent()).isEqualByComparingTo(newRepo);
        LoanService.LoanView v = loans.loanOf(a.customerId(), floating.getId());
        Loan loan = v.loan();
        assertThat(loan.getRatePercent()).isEqualByComparingTo(oldRate.add(new BigDecimal("0.50")));
        assertThat(loan.getSpreadPercent()).isEqualByComparingTo(LoanType.CAR.getSpread());
        assertThat(loan.getEndDate()).isEqualTo(floating.getEndDate());

        List<LoanInstalment> after = v.schedule();
        // Paid EMIs 1–2 and overdue EMI 3 keep their amounts; the reset starts at EMI 4
        for (int n = 0; n < 3; n++) {
            assertThat(after.get(n).getEmi()).isEqualByComparingTo(before.get(n).getEmi());
            assertThat(after.get(n).getBalanceAfter()).isEqualByComparingTo(before.get(n).getBalanceAfter());
        }
        BigDecimal newEmi = after.get(3).getEmi();
        assertThat(newEmi).isGreaterThan(before.get(3).getEmi());
        assertThat(loan.getEmi()).isEqualByComparingTo(newEmi);
        assertThat(newEmi).isEqualByComparingTo(EmiCalculator.emi(before.get(2).getBalanceAfter(), loan.getRatePercent(), 57));
        assertThat(after.get(3).getInterestPart()).isEqualByComparingTo(before.get(2).getBalanceAfter()
                .multiply(loan.getRatePercent()).divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP));
        // Still 60 EMIs on the same dates, repaying exactly the principal and ending at zero
        assertThat(after).hasSize(60);
        for (int n = 0; n < 60; n++) {
            assertThat(after.get(n).getDueDate()).isEqualTo(n == 2 ? LocalDate.now(clock).minusDays(1) : before.get(n).getDueDate());
        }
        assertThat(after.stream().map(LoanInstalment::getPrincipalPart).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("500000");
        assertThat(after.get(59).getBalanceAfter()).isEqualByComparingTo("0");

        // Recorded for both sides, and the customer is told
        assertThat(v.rateChanges()).singleElement().satisfies(c -> {
            assertThat(c.getFromInstalment()).isEqualTo(4);
            assertThat(c.getOldRate()).isEqualByComparingTo(oldRate);
            assertThat(c.getNewEmi()).isEqualByComparingTo(newEmi);
            assertThat(c.isIncrease()).isTrue();
        });
        assertThat(notifications.recentFor(customers.overview(a.customerId()).customer()))
                .anyMatch(n -> n.getSubject().equals("Interest rate increased") && n.getMessage().contains("From EMI 4"));

        // The fixed-rate loan doesn't move
        LoanService.LoanView fv = loans.loanOf(a.customerId(), fixed.getId());
        assertThat(fv.loan().getRatePercent()).isEqualByComparingTo(fixed.getRatePercent());
        assertThat(fv.schedule().get(0).getEmi()).isEqualByComparingTo(fixedBefore.get(0).getEmi());
        assertThat(fv.rateChanges()).isEmpty();
    }

    @Test
    void repoRateChangesAreValidatedTogether() {
        assertThatThrownBy(() -> rates.changeRepoRate(null, " ", "admin"))
                .hasMessageContaining("between 0.50% and 15.00%").hasMessageContaining("note");
        assertThatThrownBy(() -> rates.changeRepoRate(new BigDecimal("5.255"), "RBI", "admin")).hasMessageContaining("2 decimal");
        assertThatThrownBy(() -> rates.changeRepoRate(originalRepo, "RBI", "admin")).hasMessageContaining("already");
    }

    @Test
    void anApprovalAndARepoChangeAtTheSameMomentLeaveTheLoanPricedOffTheRealRepoRate() throws Exception {
        OpenedAccount a = accounts.active(0);
        Loan applied = loans.apply(a.customerId(), new LoanService.Application(LoanType.HOME, RateType.FLOATING,
                rs(2_000_000), 240, "Flat", "Salaried", rs(200_000)));
        BigDecimal listRate = rates.rate(LoanType.HOME, RateType.FLOATING);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        Future<?> approve = pool.submit(() -> {
            go.await();
            return loans.approve(applied.getId(), "neha", rs(2_000_000), listRate, 240);
        });
        Future<?> change = pool.submit(() -> {
            go.await();
            return rates.changeRepoRate(originalRepo.add(new BigDecimal("0.25")), "RBI policy: +0.25%", "admin");
        });
        go.countDown();
        approve.get(30, TimeUnit.SECONDS);
        change.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Whichever ran first, the loan's rate is today's repo rate plus its own spread
        Loan loan = loans.loanOf(a.customerId(), applied.getId()).loan();
        assertThat(loan.getRatePercent()).isEqualByComparingTo(rates.current().getRatePercent().add(loan.getSpreadPercent()));
    }
}
