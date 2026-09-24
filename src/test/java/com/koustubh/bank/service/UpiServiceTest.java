package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.domain.TransactionType;
import com.koustubh.bank.exception.InsufficientFundsException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.exception.WrongUpiPinException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UpiServiceTest {

    static final String UPI_PIN = "246810";
    static final String WRONG = "135791";

    @Autowired TestAccounts accounts;
    @Autowired UpiService upi;
    @Autowired AdminService admin;

    private String activate(OpenedAccount a) {
        return upi.register(a.cardNumber(), a.pin(), UPI_PIN);
    }

    @Test
    void registrationCreatesVpaFromNameAndAccountNumber() {
        OpenedAccount a = accounts.active(0);
        String vpa = activate(a);
        String last4 = a.accountNumber().substring(8);
        assertThat(vpa).isEqualTo("test." + last4 + "@javabank");
        assertThat(upi.balance(vpa, UPI_PIN)).isEqualByComparingTo("0");
        assertThat(upi.vpaForCustomer(a.customerId())).contains(vpa);
        assertThat(upi.paymentUri(vpa)).startsWith("upi://pay?pa=" + vpa + "&pn=Test%20Customer");
    }

    @Test
    void registrationNeedsCorrectAtmPinAndApprovedAccount() {
        OpenedAccount a = accounts.active(0);
        String wrongAtm = a.pin().equals("0000") ? "1111" : "0000";
        assertThatThrownBy(() -> upi.register(a.cardNumber(), wrongAtm, UPI_PIN)).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> upi.register(a.cardNumber(), a.pin(), "1234")).isInstanceOf(InvalidRequestException.class);

        OpenedAccount pending = accounts.pending();
        assertThatThrownBy(() -> upi.register(pending.cardNumber(), pending.pin(), UPI_PIN)).isInstanceOf(DisabledException.class);
    }

    @Test
    void payMovesMoneyAndRecordsUpiNarration() {
        OpenedAccount a = accounts.active(5_000);
        OpenedAccount b = accounts.active(0);
        String vpaA = activate(a);
        String vpaB = activate(b);

        assertThat(upi.lookup(vpaA, vpaB.toUpperCase()).name()).startsWith("TEST CUSTOMER");
        Transaction t = upi.pay(vpaA, vpaB, new BigDecimal("1250.50"), "Lunch", UPI_PIN);

        assertThat(t.getType()).isEqualTo(TransactionType.UPI_OUT);
        assertThat(t.getRemarks()).isEqualTo("UPI/" + vpaB + "/Lunch");
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("3749.50");
        assertThat(accounts.balanceOf(b)).isEqualByComparingTo("1250.50");
        assertThat(upi.history(vpaB).get(0).getRemarks()).isEqualTo("UPI/" + vpaA + "/Lunch");
        assertThat(upi.balance(vpaB, UPI_PIN)).isEqualByComparingTo("1250.50");
    }

    @Test
    void payRejectsBadPayeesAndInsufficientBalance() {
        OpenedAccount a = accounts.active(100);
        OpenedAccount b = accounts.active(0);
        String vpaA = activate(a);
        String vpaB = activate(b);
        assertThatThrownBy(() -> upi.pay(vpaA, vpaA, BigDecimal.TEN, null, UPI_PIN)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> upi.pay(vpaA, "nobody.0000@javabank", BigDecimal.TEN, null, UPI_PIN)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> upi.pay(vpaA, "someone@okbank", BigDecimal.TEN, null, UPI_PIN)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> upi.pay(vpaA, vpaB, new BigDecimal("100.01"), null, UPI_PIN)).isInstanceOf(InsufficientFundsException.class);
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("100");
    }

    @Test
    void dailyUpiLimitIsEnforced() {
        OpenedAccount a = accounts.active(150_000);
        OpenedAccount b = accounts.active(0);
        String vpaA = activate(a);
        String vpaB = activate(b);
        upi.pay(vpaA, vpaB, new BigDecimal("80000"), null, UPI_PIN);
        assertThatThrownBy(() -> upi.pay(vpaA, vpaB, new BigDecimal("20001"), null, UPI_PIN))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("20000");
        assertThatThrownBy(() -> upi.pay(vpaA, vpaB, new BigDecimal("100001"), null, UPI_PIN))
                .hasMessageContaining("per transaction");
    }

    @Test
    void wrongUpiPinsLockUpiAndCardResetUnlocksIt() {
        OpenedAccount a = accounts.active(1_000);
        OpenedAccount b = accounts.active(0);
        String vpaA = activate(a);
        String vpaB = activate(b);

        assertThatThrownBy(() -> upi.pay(vpaA, vpaB, BigDecimal.TEN, null, WRONG))
                .isInstanceOf(WrongUpiPinException.class).hasMessageContaining("2 attempts left");
        assertThatThrownBy(() -> upi.balance(vpaA, WRONG)).hasMessageContaining("1 attempt left");
        assertThatThrownBy(() -> upi.balance(vpaA, WRONG)).hasMessageContaining("locked");
        assertThatThrownBy(() -> upi.balance(vpaA, UPI_PIN)).hasMessageContaining("locked");
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("1000");

        // Setting a new UPI PIN with the debit card unlocks UPI and keeps the same UPI ID
        assertThat(upi.register(a.cardNumber(), a.pin(), "112233")).isEqualTo(vpaA);
        assertThat(upi.balance(vpaA, "112233")).isEqualByComparingTo("1000");
    }

    @Test
    void staffCanUnlockUpi() {
        OpenedAccount a = accounts.active(0);
        String vpa = activate(a);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> upi.balance(vpa, WRONG)).isInstanceOf(WrongUpiPinException.class);
        }
        assertThatThrownBy(() -> upi.balance(vpa, UPI_PIN)).hasMessageContaining("locked");
        admin.unblockUpi(accounts.idOf(a));
        upi.balance(vpa, UPI_PIN);
    }
}
