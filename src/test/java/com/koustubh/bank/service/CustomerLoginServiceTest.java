package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CustomerLoginServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired CustomerLoginService login;
    @Autowired AdminService admin;

    @Test
    void newCustomerGetsACustomerIdAndCanLogInCaseInsensitively() {
        OpenedAccount a = accounts.pending();
        assertThat(a.customerId()).matches("JB\\d{8}");
        // Pending customers can log in; the dashboard tells them their account is under review
        assertThat(login.verifyLogin(" " + a.customerId().toLowerCase() + " ", TestAccounts.PASSWORD)).isEqualTo(a.customerId());
        assertThatThrownBy(() -> login.verifyLogin("JB00000000", TestAccounts.PASSWORD))
                .isInstanceOf(BadCredentialsException.class).hasMessage("Invalid Customer ID or password");
    }

    @Test
    void fiveWrongPasswordsLockTheLoginAndCardResetUnlocksIt() {
        OpenedAccount a = accounts.active(0);
        for (int i = 1; i < CustomerLoginService.MAX_ATTEMPTS; i++) {
            int left = CustomerLoginService.MAX_ATTEMPTS - i;
            assertThatThrownBy(() -> login.verifyLogin(a.customerId(), "wrong-pass1"))
                    .isInstanceOf(BadCredentialsException.class).hasMessageContaining(left + (left == 1 ? " attempt" : " attempts"));
        }
        assertThatThrownBy(() -> login.verifyLogin(a.customerId(), "wrong-pass1")).isInstanceOf(LockedException.class);
        assertThatThrownBy(() -> login.verifyLogin(a.customerId(), TestAccounts.PASSWORD)).isInstanceOf(LockedException.class);

        assertThatThrownBy(() -> login.resetWithCard(a.cardNumber(), a.pin(), "short"))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(login.resetWithCard(a.cardNumber(), a.pin(), "NewPass99")).isEqualTo(a.customerId());
        login.verifyLogin(a.customerId(), "NewPass99");
    }

    @Test
    void staffCanUnlockALockedLogin() {
        OpenedAccount a = accounts.active(0);
        for (int i = 0; i < CustomerLoginService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> login.verifyLogin(a.customerId(), "wrong-pass1"));
        }
        admin.unlockLogin(accounts.idOf(a));
        login.verifyLogin(a.customerId(), TestAccounts.PASSWORD);
    }

    @Test
    void passwordCanBeChanged() {
        OpenedAccount a = accounts.active(0);
        assertThatThrownBy(() -> login.changePassword(a.customerId(), "nope", "Another123"))
                .hasMessage("Current password is incorrect");
        assertThatThrownBy(() -> login.changePassword(a.customerId(), TestAccounts.PASSWORD, "alllettersonly"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> login.changePassword(a.customerId(), TestAccounts.PASSWORD, TestAccounts.PASSWORD))
                .hasMessageContaining("different");
        login.changePassword(a.customerId(), TestAccounts.PASSWORD, "Another123");
        login.verifyLogin(a.customerId(), "Another123");
    }
}
