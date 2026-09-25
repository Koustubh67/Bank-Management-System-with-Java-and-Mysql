package com.koustubh.bank.web;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.domain.Loan;
import com.koustubh.bank.domain.LoanType;
import com.koustubh.bank.domain.RateType;
import com.koustubh.bank.service.LoanService;
import com.koustubh.bank.service.OpenedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Paying an EMI through the real pages: choose a method, card + OTP or UPI QR code, success page and receipt. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanPaymentWebTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired LoanService loans;

    private MockHttpSession login(String username, String password, String url, String userParam) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post(url).session(s).with(csrf()).param(userParam, username).param("password", password));
        return s;
    }

    private Loan activeLoan(OpenedAccount a) {
        Loan applied = loans.apply(a.customerId(), new LoanService.Application(LoanType.PERSONAL, RateType.FIXED,
                BigDecimal.valueOf(120_000), 12, "Test", "Salaried", BigDecimal.valueOf(100_000)));
        return loans.approve(applied.getId(), "neha", BigDecimal.valueOf(120_000), new BigDecimal("11.49"), 12);
    }

    @Test
    void cardDetailsThenOtpThenSuccessPageAndReceipt() throws Exception {
        OpenedAccount a = accounts.active(0);
        Loan loan = activeLoan(a);
        MockHttpSession session = login(a.customerId(), TestAccounts.PASSWORD, "/customer/login", "customerId");
        String base = "/customer/loans/" + loan.getId();

        mvc.perform(get(base).session(session)).andExpect(content().string(containsString(base + "/pay")));
        mvc.perform(get(base + "/pay").session(session))
                .andExpect(content().string(containsString("Pay EMI 1 of 12")))
                .andExpect(content().string(containsString("Show UPI QR code")))
                .andExpect(content().string(containsString("Name on card")))
                .andExpect(content().string(containsString("Savings account")));

        // Every card problem at once; what was typed comes back, except the CVV
        mvc.perform(post(base + "/pay/card").session(session).with(csrf()).param("cardNumber", "4111 1111 1111 1112")
                        .param("cardHolder", "").param("expiry", "13/30").param("cvv", "12"))
                .andExpect(redirectedUrl(base + "/pay#card"))
                .andExpect(flash().attribute("cardErrors", hasSize(4)))
                .andExpect(flash().attribute("cardNumber", "4111 1111 1111 1112"))
                .andExpect(flash().attribute("cvv", nullValue()));

        MvcResult started = mvc.perform(post(base + "/pay/card").session(session).with(csrf()).param("cardNumber", "4111 1111 1111 1111")
                        .param("cardHolder", "Test Customer").param("expiry", "12/30").param("cvv", "123"))
                .andExpect(redirectedUrlPattern("/customer/loans/payments/JBR*")).andReturn();
        String payment = started.getResponse().getRedirectedUrl();
        String otp = (String) started.getFlashMap().get("smsOtp");
        String ref = payment.substring(payment.lastIndexOf('/') + 1);

        mvc.perform(get(payment).session(session).flashAttr("smsOtp", otp))
                .andExpect(content().string(containsString("Secure card authentication")))
                .andExpect(content().string(containsString("Visa •••• 1111")))
                .andExpect(content().string(containsString(otp + " is your JavaBank OTP")))
                .andExpect(content().string(containsString("data-countdown=\"")));
        mvc.perform(post(payment + "/otp").session(session).with(csrf()).param("otp", "000000".equals(otp) ? "111111" : "000000"))
                .andExpect(flash().attribute("payError", containsString("tries left")));
        mvc.perform(post(payment + "/otp").session(session).with(csrf()).param("otp", otp)).andExpect(redirectedUrl(payment));

        mvc.perform(get(payment).session(session))
                .andExpect(content().string(containsString("Payment successful")))
                .andExpect(content().string(containsString(ref)))
                .andExpect(content().string(containsString("Receipt emailed to")));
        mvc.perform(get(payment + "/receipt").session(session))
                .andExpect(content().string(containsString("EMI Payment Receipt")))
                .andExpect(content().string(containsString("PAID")))
                .andExpect(content().string(containsString("Visa card •••• 1111")))
                .andExpect(content().string(containsString("Rupees")));
        // Shown as paid, with its receipt, on the loan page for the customer and for staff
        mvc.perform(get(base).session(session))
                .andExpect(content().string(containsString("Payments &amp; receipts")))
                .andExpect(content().string(containsString(payment + "/receipt")));
        MockHttpSession staff = login("admin", "admin123", "/admin/login", "username");
        mvc.perform(get("/admin/loans/" + loan.getId()).session(staff)).andExpect(content().string(containsString("/admin/loans/payments/" + ref + "/receipt")));
        mvc.perform(get("/admin/loans/payments/" + ref + "/receipt").session(staff)).andExpect(content().string(containsString(ref)));
        // Nobody else can see it
        MockHttpSession other = login(accounts.active(0).customerId(), TestAccounts.PASSWORD, "/customer/login", "customerId");
        mvc.perform(get(payment + "/receipt").session(other)).andExpect(status().isNotFound());
    }

    @Test
    void upiQrCodeWaitsForThePaymentThenShowsSuccess() throws Exception {
        OpenedAccount a = accounts.active(0);
        Loan loan = activeLoan(a);
        MockHttpSession session = login(a.customerId(), TestAccounts.PASSWORD, "/customer/login", "customerId");
        String payment = mvc.perform(post("/customer/loans/" + loan.getId() + "/pay/upi").session(session).with(csrf()))
                .andExpect(redirectedUrlPattern("/customer/loans/payments/JBR*")).andReturn().getResponse().getRedirectedUrl();

        mvc.perform(get(payment).session(session))
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("loans.javabank@javabank")))
                .andExpect(content().string(containsString("data-poll=\"" + payment + "/status\"")))
                .andExpect(content().string(anyOf(containsString("data-countdown=\"300\""), containsString("data-countdown=\"299\""))));
        mvc.perform(get(payment + "/status").session(session)).andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(post(payment + "/upi-paid").session(session).with(csrf())).andExpect(redirectedUrl(payment));
        mvc.perform(get(payment + "/status").session(session)).andExpect(jsonPath("$.status").value("PAID"));
        mvc.perform(get(payment).session(session))
                .andExpect(content().string(containsString("Payment successful")))
                .andExpect(content().string(containsString("UPI transaction ID (UTR)")));
        assertThat(loans.loanOf(a.customerId(), loan.getId()).paid()).isEqualTo(1);
        // Cancelling or paying again afterwards changes nothing
        mvc.perform(post(payment + "/cancel").session(session).with(csrf())).andExpect(flash().attribute("message", containsString("already paid")));
        assertThat(loans.loanOf(a.customerId(), loan.getId()).paid()).isEqualTo(1);
    }
}
