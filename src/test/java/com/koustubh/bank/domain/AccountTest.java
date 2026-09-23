package com.koustubh.bank.domain;

import com.koustubh.bank.exception.AccountNotActiveException;
import com.koustubh.bank.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private Account newAccount() {
        return new Account("100000000001", new Customer(), AccountType.SAVINGS, "", LocalDateTime.now());
    }

    @Test
    void newAccountIsPendingWithZeroBalance() {
        Account account = newAccount();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void pendingAccountCannotTransact() {
        assertThatThrownBy(() -> newAccount().credit(BigDecimal.TEN))
                .isInstanceOf(AccountNotActiveException.class);
    }

    @Test
    void creditAndDebitChangeBalance() {
        Account account = newAccount();
        account.activate();
        account.credit(new BigDecimal("1000.50"));
        account.debit(new BigDecimal("200.25"));
        assertThat(account.getBalance()).isEqualByComparingTo("800.25");
    }

    @Test
    void cannotDebitMoreThanBalance() {
        Account account = newAccount();
        account.activate();
        account.credit(new BigDecimal("100"));
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalance()).isEqualByComparingTo("100");
    }

    @Test
    void frozenAccountCannotTransact() {
        Account account = newAccount();
        account.activate();
        account.freeze();
        assertThatThrownBy(() -> account.debit(BigDecimal.ONE)).isInstanceOf(AccountNotActiveException.class);
    }
}
