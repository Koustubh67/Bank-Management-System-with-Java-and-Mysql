package com.koustubh.bank.service;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.*;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.EmiPaymentRepository;
import com.koustubh.bank.repository.LoanInstalmentRepository;
import com.koustubh.bank.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Paying EMIs by card (details + OTP), UPI QR code and savings account: the 5-minute window and no double payments. */
@SpringBootTest
@ActiveProfiles("test")
class EmiPaymentServiceTest {

    @Autowired TestAccounts accounts;
    @Autowired LoanService loans;
    @Autowired EmiPaymentService payments;
    @Autowired EmiPaymentRepository paymentRows;
    @Autowired LoanInstalmentRepository instalments;
    @Autowired NotificationRepository notificationRows;
    @Autowired NotificationService notifications;
    @Autowired CustomerService customers;
    @Autowired PlatformTransactionManager txManager;
    @Autowired Clock clock;

    private static final EmiPaymentService.Card VISA = new EmiPaymentService.Card("4111 1111 1111 1111", "Rahul  Sharma", "12/30", "123");

    private static BigDecimal rs(long v) {
        return BigDecimal.valueOf(v);
    }

    private Loan activeLoan(OpenedAccount a) {
        Loan applied = loans.apply(a.customerId(), new LoanService.Application(LoanType.PERSONAL, RateType.FIXED, rs(120_000), 12,
                "Test", "Salaried", rs(100_000)));
        return loans.approve(applied.getId(), "neha", rs(120_000), new BigDecimal("11.49"), 12);
    }

    private EmiPayment row(String reference) {
        return paymentRows.findByReference(reference).orElseThrow();
    }

    private int paidEmis(OpenedAccount a, Loan loan) {
        return loans.loanOf(a.customerId(), loan.getId()).paid();
    }

    /** As if the 5 minutes to pay have gone by. */
    private void timeUp(String reference) {
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                ReflectionTestUtils.setField(row(reference), "expiresAt", LocalDateTime.now(clock).minusSeconds(1)));
    }

    @Test
    void cardDetailsAreCheckedTogetherAndTheNumberAndCvvAreNeverStored() throws Exception {
        OpenedAccount a = accounts.active(0);
        Loan loan = activeLoan(a);
        assertThatThrownBy(() -> payments.startCard(a.customerId(), loan.getId(), new EmiPaymentService.Card("4111 1111 1111 1112", "", "13/30", "12")))
                .hasMessageContaining("valid card number").hasMessageContaining("name as printed")
                .hasMessageContaining("MM/YY").hasMessageContaining("3-digit CVV");
        assertThatThrownBy(() -> payments.startCard(a.customerId(), loan.getId(), new EmiPaymentService.Card("4111111111111111", "R Sharma", "01/20", "123")))
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> payments.startCard(a.customerId(), loan.getId(), new EmiPaymentService.Card("abcd efgh ijkl", "R Sharma", "12/30", "123")))
                .hasMessageContaining("valid card number");
        assertThatThrownBy(() -> payments.startCard(a.customerId(), loan.getId(), new EmiPaymentService.Card("5040930000000017", "R Sharma", "12/30", "123")))
                .hasMessageContaining("JavaBank debit card");

        EmiPaymentService.CardStarted started = payments.startCard(a.customerId(), loan.getId(), VISA);
        EmiPayment p = row(started.payment().getReference());
        assertThat(p.getStatus()).isEqualTo(EmiPayment.Status.PENDING);
        assertThat(p.getCardNetwork()).isEqualTo("Visa");
        assertThat(p.getCardLast4()).isEqualTo("1111");
        assertThat(p.getCardHolder()).isEqualTo("RAHUL SHARMA");
        assertThat(p.getExpiresAt()).isEqualTo(p.getCreatedAt().plusMinutes(5));
        assertThat(p.getOtpHash()).startsWith("$2").doesNotContain(started.otp());
        for (Field f : EmiPayment.class.getDeclaredFields()) {
            f.setAccessible(true);
            assertThat(String.valueOf(f.get(p))).doesNotContain("4111111111111111");
            assertThat(f.getName().toLowerCase()).doesNotContain("cvv");
        }
        Customer c = customers.overview(a.customerId()).customer();
        assertThat(notificationRows.findTop10ByCustomerIdAndChannelOrderByIdDesc(c.getId(), Notification.Channel.SMS).get(0).getMessage())
                .startsWith(started.otp() + " is your JavaBank OTP").contains("card ending 1111");
    }

    @Test
    void theRightOtpPaysTheEmiAndThreeWrongOnesStopThePayment() {
        OpenedAccount a = accounts.active(0);
        Loan loan = activeLoan(a);
        BigDecimal balance = accounts.balanceOf(a);
        EmiPaymentService.CardStarted first = payments.startCard(a.customerId(), loan.getId(), VISA);
        String ref = first.payment().getReference();
        String wrong = "000000".equals(first.otp()) ? "111111" : "000000";
        assertThatThrownBy(() -> payments.confirmCard(a.customerId(), ref, wrong)).hasMessageContaining("2 tries left");
        assertThatThrownBy(() -> payments.confirmCard(a.customerId(), ref, wrong)).hasMessageContaining("1 try left");
        assertThatThrownBy(() -> payments.confirmCard(a.customerId(), ref, wrong)).hasMessageContaining("stopped");
        assertThat(row(ref).getStatus()).isEqualTo(EmiPayment.Status.FAILED);
        assertThatThrownBy(() -> payments.confirmCard(a.customerId(), ref, first.otp())).hasMessageContaining("failed");
        assertThat(paidEmis(a, loan)).isZero();

        EmiPaymentService.CardStarted second = payments.startCard(a.customerId(), loan.getId(), VISA);
        String ref2 = second.payment().getReference();
        EmiPayment paid = payments.confirmCard(a.customerId(), ref2, second.otp());
        assertThat(paid.getStatus()).isEqualTo(EmiPayment.Status.PAID);
        assertThat(paid.getTransactionId()).matches("\\d{6}");
        assertThat(paidEmis(a, loan)).isEqualTo(1);
        // Card money comes from outside the bank: the savings account is untouched
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo(balance);
        // Confirming again (double click, back button) doesn't pay twice
        assertThat(payments.confirmCard(a.customerId(), ref2, second.otp()).getReference()).isEqualTo(ref2);
        assertThat(paidEmis(a, loan)).isEqualTo(1);

        EmiPaymentService.Receipt r = payments.receipt(a.customerId(), ref2);
        assertThat(r.instalment().getNumber()).isEqualTo(1);
        assertThat(r.next().getNumber()).isEqualTo(2);
        assertThat(r.paid()).isEqualTo(1);
        assertThat(notifications.recentFor(customers.overview(a.customerId()).customer()).get(0)).satisfies(n -> {
            assertThat(n.getSubject()).isEqualTo("EMI paid");
            assertThat(n.getMessage()).contains(ref2).contains("Visa card •••• 1111").contains("Next EMI");
        });
    }

    @Test
    void aUpiQrCodeCanBePaidForFiveMinutesOnly() {
        OpenedAccount a = accounts.active(0);
        Loan loan = activeLoan(a);
        EmiPayment qr = payments.startUpi(a.customerId(), loan.getId());
        assertThat(payments.upiLink(qr)).startsWith("upi://pay?pa=loans.javabank@javabank")
                .contains("am=" + qr.getAmount().toPlainString()).contains("tr=" + qr.getReference());

        timeUp(qr.getReference());
        assertThatThrownBy(() -> payments.upiPaid(a.customerId(), qr.getReference())).hasMessageContaining("5 minutes");
        assertThat(row(qr.getReference()).getStatus()).isEqualTo(EmiPayment.Status.EXPIRED);
        assertThat(paidEmis(a, loan)).isZero();

        EmiPayment again = payments.startUpi(a.customerId(), loan.getId());
        EmiPayment paid = payments.upiPaid(a.customerId(), again.getReference());
        assertThat(paid.getStatus()).isEqualTo(EmiPayment.Status.PAID);
        assertThat(paid.getTransactionId()).matches("\\d{12}");
        assertThat(paidEmis(a, loan)).isEqualTo(1);
    }

    @Test
    void unfinishedPaymentsExpireOnTheirOwn() {
        OpenedAccount a = accounts.active(0);
        EmiPayment qr = payments.startUpi(a.customerId(), activeLoan(a).getId());
        timeUp(qr.getReference());
        assertThat(payments.expireStale()).isGreaterThanOrEqualTo(1);
        assertThat(row(qr.getReference()).getStatus()).isEqualTo(EmiPayment.Status.EXPIRED);
        assertThat(row(qr.getReference()).getFailureReason()).isEqualTo("Not paid within 5 minutes");
    }

    @Test
    void anEmiIsNeverPaidTwice() {
        OpenedAccount a = accounts.active(50_000);
        Loan loan = activeLoan(a);
        // A new payment replaces one still pending
        EmiPayment qr = payments.startUpi(a.customerId(), loan.getId());
        EmiPaymentService.CardStarted card = payments.startCard(a.customerId(), loan.getId(), VISA);
        assertThat(row(qr.getReference()).getStatus()).isEqualTo(EmiPayment.Status.CANCELLED);
        assertThatThrownBy(() -> payments.upiPaid(a.customerId(), qr.getReference())).hasMessageContaining("cancelled");
        // Paying from the account while the card waits for its OTP: the card payment is cancelled, nothing charged
        assertThat(payments.payFromAccount(a.customerId(), loan.getId()).getInstalmentNumber()).isEqualTo(1);
        assertThatThrownBy(() -> payments.confirmCard(a.customerId(), card.payment().getReference(), card.otp()))
                .hasMessageContaining("cancelled");
        assertThat(paidEmis(a, loan)).isEqualTo(1);

        // A QR code for EMI 2 is open when the daily auto-debit pays EMI 2: the QR code can't take the money again
        EmiPayment qr2 = payments.startUpi(a.customerId(), loan.getId());
        assertThat(qr2.getInstalmentNumber()).isEqualTo(2);
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                ReflectionTestUtils.setField(instalments.findById(qr2.getInstalment().getId()).orElseThrow(), "dueDate",
                        LocalDate.now(clock).minusDays(1)));
        loans.collectDueEmis();
        assertThatThrownBy(() -> payments.upiPaid(a.customerId(), qr2.getReference()))
                .hasMessageContaining("already paid").hasMessageContaining("Nothing was charged");
        assertThat(row(qr2.getReference()).getStatus()).isEqualTo(EmiPayment.Status.CANCELLED);
        assertThat(paidEmis(a, loan)).isEqualTo(2);
        // The auto-debit issued its own receipt
        assertThat(payments.paymentsOf(loan.getId())).anyMatch(p -> p.getMethod() == EmiPayment.Method.AUTO_DEBIT
                && p.getStatus() == EmiPayment.Status.PAID && p.getInstalmentNumber() == 2);
    }

    @Test
    void twoTabsConfirmingTheSameQrCodeAtOncePayOnce() throws Exception {
        OpenedAccount a = accounts.active(0);
        Loan loan = activeLoan(a);
        String ref = payments.startUpi(a.customerId(), loan.getId()).getReference();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<EmiPayment>> tabs = List.of(1, 2).stream().map(n -> pool.submit(() -> {
            go.await();
            return payments.upiPaid(a.customerId(), ref);
        })).toList();
        go.countDown();
        for (Future<EmiPayment> tab : tabs) {
            assertThat(tab.get(30, TimeUnit.SECONDS).getStatus()).isEqualTo(EmiPayment.Status.PAID);
        }
        pool.shutdown();
        assertThat(paidEmis(a, loan)).isEqualTo(1);
        assertThat(payments.paymentsOf(loan.getId()).stream().filter(p -> p.getStatus() == EmiPayment.Status.PAID)).hasSize(1);
        assertThat(notifications.recentFor(customers.overview(a.customerId()).customer()).stream()
                .filter(n -> n.getSubject().equals("EMI paid"))).hasSize(1);
    }

    @Test
    void onlyTheOwnerCanPayOrSeeTheReceipt() {
        OpenedAccount a = accounts.active(0);
        OpenedAccount b = accounts.active(0);
        Loan loan = activeLoan(a);
        EmiPayment qr = payments.startUpi(a.customerId(), loan.getId());
        assertThatThrownBy(() -> payments.upiPaid(b.customerId(), qr.getReference())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> payments.startCard(b.customerId(), loan.getId(), VISA)).isInstanceOf(NotFoundException.class);
        payments.upiPaid(a.customerId(), qr.getReference());
        assertThatThrownBy(() -> payments.receipt(b.customerId(), qr.getReference())).isInstanceOf(NotFoundException.class);
        assertThat(payments.receiptForStaff(qr.getReference()).customer().getCustomerId()).isEqualTo(a.customerId());
    }

    @Test
    void cardNetworksAndTheLuhnCheck() {
        assertThat(EmiPaymentService.network("4111111111111111")).isEqualTo("Visa");
        assertThat(EmiPaymentService.network("5555555555554444")).isEqualTo("Mastercard");
        assertThat(EmiPaymentService.network("2221000000000009")).isEqualTo("Mastercard");
        assertThat(EmiPaymentService.network("378282246310005")).isEqualTo("Amex");
        assertThat(EmiPaymentService.network("6521111111111117")).isEqualTo("RuPay");
        assertThat(EmiPaymentService.luhn("4111111111111111")).isTrue();
        assertThat(EmiPaymentService.luhn("4111111111111112")).isFalse();
        assertThat(EmiPaymentService.luhn("378282246310005")).isTrue();
    }
}
