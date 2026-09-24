package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.InsufficientFundsException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.LoanInstalmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LoanServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired LoanService loans;
    @Autowired LoanInstalmentRepository instalments;
    @Autowired CustomerService customers;
    @Autowired NotificationService notifications;
    @Autowired PlatformTransactionManager txManager;
    @Autowired LendingRateService rates;

    private static BigDecimal rs(long v) {
        return BigDecimal.valueOf(v);
    }

    private LoanService.Application car(long amount, int months, long income) {
        return new LoanService.Application(LoanType.CAR, RateType.FIXED, rs(amount), months, "New car", "Salaried", rs(income));
    }

    /** Moves an EMI's due date, e.g. into the past so the collection job picks it up. */
    private void setDue(Long instalmentId, LocalDate due) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            LoanInstalment i = instalments.findById(instalmentId).orElseThrow();
            ReflectionTestUtils.setField(i, "dueDate", due);
            instalments.save(i);
        });
    }

    @Test
    void applicationProblemsAreReportedTogether() {
        OpenedAccount a = accounts.active(0);
        LoanService.Application bad = new LoanService.Application(LoanType.CAR, null, rs(10), 3, " ", "Astronaut", rs(500));
        assertThatThrownBy(() -> loans.apply(a.customerId(), bad))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("amount must be between").hasMessageContaining("tenure must be between")
                .hasMessageContaining("employment").hasMessageContaining("income").hasMessageContaining("what the loan is for")
                .hasMessageContaining("fixed or floating");
    }

    @Test
    void unaffordableDuplicateAndInactiveApplicationsAreRefused() {
        OpenedAccount a = accounts.active(0);
        assertThatThrownBy(() -> loans.apply(a.customerId(), car(2_000_000, 12, 50_000)))
                .hasMessageContaining("50%");
        loans.apply(a.customerId(), car(500_000, 60, 80_000));
        assertThatThrownBy(() -> loans.apply(a.customerId(), car(300_000, 60, 80_000)))
                .hasMessageContaining("already have a car loan application");
        OpenedAccount pending = accounts.pending();
        assertThatThrownBy(() -> loans.apply(pending.customerId(), car(500_000, 60, 80_000)))
                .hasMessageContaining("account must be active");
    }

    @Test
    void approvalDisbursesCreatesTheScheduleAndCanOnlyHappenOnce() {
        OpenedAccount a = accounts.active(1_000);
        Loan applied = loans.apply(a.customerId(), car(600_000, 60, 90_000));
        assertThatThrownBy(() -> loans.approve(applied.getId(), "neha", rs(700_000), new BigDecimal("8.75"), 60))
                .hasMessageContaining("more than");

        Loan loan = loans.approve(applied.getId(), "neha", rs(500_000), new BigDecimal("8.50"), 48);

        assertThat(loan.getStatus()).isEqualTo(LoanStatus.ACTIVE);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("501000");
        assertThat(loan.getFirstEmiDate()).isEqualTo(EmiCalculator.firstEmiDate(LocalDate.now()));
        assertThat(loan.getEndDate()).isEqualTo(loan.getFirstEmiDate().plusMonths(47));
        assertThat(loan.getEmi()).isEqualByComparingTo(EmiCalculator.emi(rs(500_000), new BigDecimal("8.50"), 48));
        LoanService.LoanView v = loans.loanOf(a.customerId(), loan.getId());
        assertThat(v.schedule()).hasSize(48);
        assertThat(v.next().getNumber()).isEqualTo(1);
        assertThat(customers.overview(a.customerId()).recent().get(0).getType()).isEqualTo(TransactionType.LOAN_DISBURSAL);
        assertThat(notifications.recentFor(customers.overview(a.customerId()).customer()).get(0).getSubject())
                .isEqualTo("Your loan is approved");

        assertThatThrownBy(() -> loans.approve(applied.getId(), "neha", rs(500_000), new BigDecimal("8.50"), 48))
                .hasMessageContaining("already been active");
        assertThatThrownBy(() -> loans.reject(applied.getId(), "neha", "late")).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void theCustomersRateChoiceSetsTheQuoteAndFloatingLoansKeepTheirSpread() {
        OpenedAccount a = accounts.active(0);
        Loan floating = loans.apply(a.customerId(), new LoanService.Application(LoanType.HOME, RateType.FLOATING,
                rs(3_000_000), 240, "Flat", "Salaried", rs(150_000)));
        Loan fixed = loans.apply(a.customerId(), new LoanService.Application(LoanType.PERSONAL, RateType.FIXED,
                rs(200_000), 24, "Wedding", "Salaried", rs(150_000)));
        BigDecimal repo = rates.current().getRatePercent();

        LoanService.LoanView fv = loans.loanOf(a.customerId(), floating.getId());
        assertThat(fv.quotedRate()).isEqualByComparingTo(repo.add(LoanType.HOME.getSpread()));
        assertThat(fv.requestedQuote().emi()).isEqualByComparingTo(EmiCalculator.emi(rs(3_000_000), fv.quotedRate(), 240));
        LoanService.LoanView xv = loans.loanOf(a.customerId(), fixed.getId());
        assertThat(xv.quotedRate()).isEqualByComparingTo(repo.add(LoanType.PERSONAL.getSpread()).add(LoanType.PERSONAL.getFixedPremium()));

        // A floating rate can't be sanctioned below the repo rate; otherwise the spread is remembered for life
        assertThatThrownBy(() -> loans.approve(floating.getId(), "neha", rs(3_000_000), repo.subtract(BigDecimal.ONE), 240))
                .hasMessageContaining("below the repo rate");
        Loan approved = loans.approve(floating.getId(), "neha", rs(3_000_000), repo.add(new BigDecimal("3.10")), 240);
        assertThat(approved.getRateType()).isEqualTo(RateType.FLOATING);
        assertThat(approved.getSpreadPercent()).isEqualByComparingTo("3.10");
        Loan approvedFixed = loans.approve(fixed.getId(), "neha", rs(200_000), new BigDecimal("11.49"), 24);
        assertThat(approvedFixed.getSpreadPercent()).isNull();
    }

    @Test
    void twoOfficersApprovingAtTheSameTimeDisburseOnlyOnce() throws Exception {
        OpenedAccount a = accounts.active(0);
        Loan applied = loans.apply(a.customerId(), car(400_000, 36, 90_000));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<Object>> tasks = List.of(1, 2).stream().map(n -> pool.submit(() -> {
            go.await();
            try {
                loans.approve(applied.getId(), "officer" + n, rs(400_000), new BigDecimal("8.75"), 36);
                ok.incrementAndGet();
            } catch (InvalidRequestException expected) {
                // the second officer sees it's already decided
            }
            return null;
        })).toList();
        go.countDown();
        for (Future<Object> t : tasks) t.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(ok.get()).isEqualTo(1);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("400000");
        assertThat(loans.loanOf(a.customerId(), applied.getId()).schedule()).hasSize(36);
    }

    @Test
    void dueEmisAreDebitedAndLowBalanceMakesThemOverdueUntilPaid() {
        OpenedAccount a = accounts.active(0);
        Loan loan = loans.approve(loans.apply(a.customerId(), car(100_000, 12, 50_000)).getId(), "neha",
                rs(100_000), new BigDecimal("10"), 12);
        List<LoanInstalment> schedule = loans.loanOf(a.customerId(), loan.getId()).schedule();
        setDue(schedule.get(0).getId(), LocalDate.now().minusDays(1));

        assertThat(loans.collectDueEmis()).isGreaterThanOrEqualTo(1);
        LoanService.LoanView v = loans.loanOf(a.customerId(), loan.getId());
        assertThat(v.paid()).isEqualTo(1);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo(rs(100_000).subtract(schedule.get(0).getEmi()));
        assertThat(v.loan().getOutstanding()).isEqualByComparingTo(schedule.get(0).getBalanceAfter());
        assertThat(customers.overview(a.customerId()).recent().get(0).getRemarks()).startsWith("EMI 1/12/");

        // Spend almost everything: the next due EMI can't be paid and becomes overdue (alerted once)
        accounts.spendAllBut(a, rs(100));
        setDue(schedule.get(1).getId(), LocalDate.now());
        loans.collectDueEmis();
        loans.collectDueEmis();
        v = loans.loanOf(a.customerId(), loan.getId());
        assertThat(v.overdue()).isEqualTo(1);
        long overdueAlerts = notifications.recentFor(customers.overview(a.customerId()).customer()).stream()
                .filter(n -> n.getSubject().equals("EMI overdue")).count();
        assertThat(overdueAlerts).isEqualTo(1);

        // Money arrives: the next run clears it
        accounts.deposit(a, rs(20_000));
        loans.collectDueEmis();
        v = loans.loanOf(a.customerId(), loan.getId());
        assertThat(v.overdue()).isZero();
        assertThat(v.paid()).isEqualTo(2);
    }

    @Test
    void payingEveryEmiEarlyClosesTheLoan() {
        OpenedAccount a = accounts.active(5_000);
        Loan loan = loans.approve(loans.apply(a.customerId(), new LoanService.Application(LoanType.GOLD, RateType.FIXED, rs(60_000), 6,
                "Business stock", "Business owner", rs(40_000))).getId(), "neha", rs(60_000), new BigDecimal("9"), 6);
        for (int n = 1; n <= 6; n++) {
            assertThat(loans.payNext(a.customerId(), loan.getId()).getNumber()).isEqualTo(n);
        }
        LoanService.LoanView v = loans.loanOf(a.customerId(), loan.getId());
        assertThat(v.loan().getStatus()).isEqualTo(LoanStatus.CLOSED);
        assertThat(v.loan().getOutstanding()).isEqualByComparingTo("0");
        // 5,000 + 60,000 borrowed − (60,000 principal + interest) repaid
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo(rs(5_000).subtract(v.totalInterest()));
        assertThat(notifications.recentFor(customers.overview(a.customerId()).customer()).get(0).getSubject())
                .isEqualTo("Loan closed");
        assertThatThrownBy(() -> loans.payNext(a.customerId(), loan.getId())).hasMessageContaining("no EMIs left");
    }

    @Test
    void payNextNeedsEnoughBalanceAndOnlyTheOwnerCanPay() {
        OpenedAccount a = accounts.active(0);
        OpenedAccount b = accounts.active(0);
        Loan loan = loans.approve(loans.apply(a.customerId(), car(100_000, 12, 50_000)).getId(), "neha",
                rs(100_000), new BigDecimal("10"), 12);
        assertThatThrownBy(() -> loans.payNext(b.customerId(), loan.getId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> loans.loanOf(b.customerId(), loan.getId())).isInstanceOf(NotFoundException.class);
        accounts.spendAllBut(a, rs(10));
        assertThatThrownBy(() -> loans.payNext(a.customerId(), loan.getId())).isInstanceOf(InsufficientFundsException.class);
        assertThat(loans.loanOf(a.customerId(), loan.getId()).paid()).isZero();
    }

    @Test
    void publicEnquiriesAreValidatedAndHandledByStaff() {
        assertThatThrownBy(() -> loans.enquire(new LoanService.Enquiry("", "123", "x", "", LoanType.HOME, rs(10), "?", rs(1), "Night")))
                .hasMessageContaining("name").hasMessageContaining("mobile").hasMessageContaining("email")
                .hasMessageContaining("city").hasMessageContaining("amount").hasMessageContaining("call");
        LoanEnquiry e = loans.enquire(new LoanService.Enquiry("Asha Rao", "9812345678", "asha@example.com", "Pune",
                LoanType.HOME, rs(4_000_000), "Salaried", rs(150_000), LoanService.CALL_TIMES.get(2)));
        assertThat(e.getReference()).startsWith("JBE");
        assertThatThrownBy(() -> loans.updateEnquiry(e.getId(), LoanEnquiry.Status.CLOSED, "neha", "")).hasMessageContaining("note");
        loans.updateEnquiry(e.getId(), LoanEnquiry.Status.CONTACTED, "neha", "Documents requested");
        assertThat(loans.recentEnquiries()).anyMatch(x -> x.getId().equals(e.getId()) && x.getStatus() == LoanEnquiry.Status.CONTACTED);
    }
}
