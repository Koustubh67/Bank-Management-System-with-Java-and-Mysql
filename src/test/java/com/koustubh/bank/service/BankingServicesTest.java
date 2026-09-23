package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.dto.SignupForm;
import com.koustubh.bank.exception.DailyLimitExceededException;
import com.koustubh.bank.exception.InsufficientFundsException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the services against a real (in-memory H2) database with the real Flyway schema. */
@SpringBootTest
@ActiveProfiles("test")
class BankingServicesTest {

    @Autowired TestAccounts accounts;
    @Autowired AccountOpeningService opening;
    @Autowired AtmService atm;
    @Autowired TransferService transfers;
    @Autowired CardSecurityService cardSecurity;
    @Autowired AdminService admin;

    private static BigDecimal rs(long amount) {
        return BigDecimal.valueOf(amount);
    }

    // ----- Account opening -----

    @Test
    void duplicatePanIsRejected() {
        SignupForm form = TestAccounts.form();
        opening.open(form);
        SignupForm again = TestAccounts.form();
        again.setPan(form.getPan());
        assertThatThrownBy(() -> opening.open(again))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("PAN");
    }

    // ----- Deposit / withdrawal -----

    @Test
    void depositIncreasesBalanceAndIsRecorded() {
        OpenedAccount a = accounts.active(0);
        Transaction t = atm.deposit(a.cardNumber(), rs(5000));
        assertThat(t.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(t.getBalanceAfter()).isEqualByComparingTo("5000");
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("5000");
        assertThat(atm.miniStatement(a.cardNumber())).hasSize(1);
    }

    @Test
    void cashAmountsMustBeMultiplesOf100() {
        OpenedAccount a = accounts.active(1000);
        assertThatThrownBy(() -> atm.withdraw(a.cardNumber(), rs(150)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> atm.deposit(a.cardNumber(), rs(-100)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void cannotWithdrawMoreThanBalance() {
        OpenedAccount a = accounts.active(1000);
        assertThatThrownBy(() -> atm.withdraw(a.cardNumber(), rs(1100)))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("1000");
    }

    @Test
    void dailyWithdrawalLimitIsEnforced() {
        OpenedAccount a = accounts.active(50_000);
        atm.withdraw(a.cardNumber(), rs(20_000));
        assertThatThrownBy(() -> atm.withdraw(a.cardNumber(), rs(6_000)))
                .isInstanceOf(DailyLimitExceededException.class)
                .hasMessageContaining("5000");
        atm.withdraw(a.cardNumber(), rs(5_000));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("25000");
    }

    @Test
    void frozenAccountCannotWithdraw() {
        OpenedAccount a = accounts.active(1000);
        admin.freeze(accounts.idOf(a));
        assertThatThrownBy(() -> atm.withdraw(a.cardNumber(), rs(100)))
                .hasMessageContaining("frozen");
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        OpenedAccount a = accounts.active(1000);
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    atm.withdraw(a.cardNumber(), rs(100));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException expected) {
                    // the account ran out of money: correct behaviour
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("0");
    }

    // ----- Fund transfer -----

    @Test
    void transferMovesMoneyBetweenAccounts() {
        OpenedAccount from = accounts.active(10_000);
        OpenedAccount to = accounts.active(0);

        Transaction out = transfers.transfer(from.cardNumber(), to.accountNumber(), new BigDecimal("2500.50"));

        assertThat(accounts.balanceOf(from)).isEqualByComparingTo("7499.50");
        assertThat(accounts.balanceOf(to)).isEqualByComparingTo("2500.50");
        Transaction in = atm.miniStatement(to.cardNumber()).get(0);
        assertThat(in.getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(in.getReferenceId()).isEqualTo(out.getReferenceId());
        assertThat(in.getCounterpartyAccount()).isEqualTo(from.accountNumber());
    }

    @Test
    void failedTransferChangesNothing() {
        OpenedAccount from = accounts.active(1_000);
        OpenedAccount to = accounts.active(0);
        admin.freeze(accounts.idOf(to));

        assertThatThrownBy(() -> transfers.transfer(from.cardNumber(), to.accountNumber(), rs(500)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> transfers.transfer(from.cardNumber(), to.accountNumber(), rs(5_000)))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(accounts.balanceOf(from)).isEqualByComparingTo("1000");
        assertThat(accounts.balanceOf(to)).isEqualByComparingTo("0");
    }

    @Test
    void transferNeedsEnoughBalance() {
        OpenedAccount from = accounts.active(1_000);
        OpenedAccount to = accounts.active(0);
        assertThatThrownBy(() -> transfers.transfer(from.cardNumber(), to.accountNumber(), rs(1_001)))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(accounts.balanceOf(to)).isEqualByComparingTo("0");
    }

    @Test
    void cannotTransferToOwnOrUnknownAccount() {
        OpenedAccount a = accounts.active(1_000);
        assertThatThrownBy(() -> transfers.transfer(a.cardNumber(), a.accountNumber(), rs(100)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> transfers.transfer(a.cardNumber(), "999999999999", rs(100)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void simultaneousTransfersInOppositeDirectionsDoNotDeadlock() throws Exception {
        OpenedAccount a = accounts.active(10_000);
        OpenedAccount b = accounts.active(10_000);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> transfers.transfer(a.cardNumber(), b.accountNumber(), rs(100))));
            futures.add(pool.submit(() -> transfers.transfer(b.cardNumber(), a.accountNumber(), rs(100))));
        }
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("10000");
        assertThat(accounts.balanceOf(b)).isEqualByComparingTo("10000");
    }

    // ----- Card security -----

    @Test
    void cardIsBlockedAfterThreeWrongPins() {
        OpenedAccount a = accounts.active(0);
        String wrong = a.pin().equals("0000") ? "1111" : "0000";

        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), wrong))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("2 attempts left");
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), wrong))
                .isInstanceOf(BadCredentialsException.class).hasMessageContaining("1 attempt left");
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), wrong))
                .isInstanceOf(LockedException.class);
        // Even the right PIN is refused now
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), a.pin()))
                .isInstanceOf(LockedException.class);

        admin.unblockCard(accounts.idOf(a));
        cardSecurity.verifyLogin(a.cardNumber(), a.pin());
    }

    @Test
    void successfulLoginResetsWrongPinCounter() {
        OpenedAccount a = accounts.active(0);
        String wrong = a.pin().equals("0000") ? "1111" : "0000";
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), wrong)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), wrong)).isInstanceOf(BadCredentialsException.class);
        cardSecurity.verifyLogin(a.cardNumber(), a.pin());
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), wrong))
                .hasMessageContaining("2 attempts left");
    }

    @Test
    void pendingAccountCannotLogIn() {
        OpenedAccount a = accounts.pending();
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), a.pin()))
                .isInstanceOf(DisabledException.class).hasMessageContaining("approval");
    }

    @Test
    void pinCanBeChanged() {
        OpenedAccount a = accounts.active(0);
        String newPin = a.pin().equals("4321") ? "1234" : "4321";
        assertThatThrownBy(() -> cardSecurity.changePin(a.cardNumber(), "xxxx", newPin))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> cardSecurity.changePin(a.cardNumber(), a.pin(), a.pin()))
                .isInstanceOf(InvalidRequestException.class);

        cardSecurity.changePin(a.cardNumber(), a.pin(), newPin);
        cardSecurity.verifyLogin(a.cardNumber(), newPin);
        assertThatThrownBy(() -> cardSecurity.verifyLogin(a.cardNumber(), a.pin()))
                .isInstanceOf(BadCredentialsException.class);
    }
}
