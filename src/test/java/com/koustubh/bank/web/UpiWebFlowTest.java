package com.koustubh.bank.web;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.service.OpenedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** JavaPay UPI is opened from the logged-in customer's dashboard. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpiWebFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;

    private MockHttpSession login(OpenedAccount a) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/customer/login").session(session).with(csrf())
                        .param("customerId", a.customerId()).param("password", TestAccounts.PASSWORD))
                .andExpect(redirectedUrl("/customer"));
        return session;
    }

    private String activate(OpenedAccount a, MockHttpSession session) throws Exception {
        mvc.perform(get("/upi").session(session)).andExpect(redirectedUrl("/upi/register"));
        String msg = (String) mvc.perform(post("/upi/register").session(session).with(csrf())
                        .param("atmPin", a.pin()).param("upiPin", "246810").param("confirmPin", "246810"))
                .andExpect(redirectedUrl("/upi")).andReturn().getFlashMap().get("message");
        return msg.substring(msg.lastIndexOf(' ') + 1);
    }

    @Test
    void activatePayReceiveBalanceAndHistory() throws Exception {
        OpenedAccount a = accounts.active(5_000);
        OpenedAccount b = accounts.active(0);
        MockHttpSession sa = login(a);
        MockHttpSession sb = login(b);
        String vpaA = activate(a, sa);
        String vpaB = activate(b, sb);

        mvc.perform(get("/upi").session(sa)).andExpect(status().isOk()).andExpect(content().string(containsString(vpaA)));
        mvc.perform(post("/upi/pay").session(sa).with(csrf()).param("payeeVpa", vpaB).param("amount", "750").param("note", "Movie"))
                .andExpect(view().name("upi/confirm"))
                .andExpect(content().string(containsString("TEST CUSTOMER")));
        mvc.perform(post("/upi/pay/confirm").session(sa).with(csrf())
                        .param("payeeVpa", vpaB).param("amount", "750").param("note", "Movie").param("pin", "000000"))
                .andExpect(model().attribute("error", containsString("2 attempts left")));
        mvc.perform(post("/upi/pay/confirm").session(sa).with(csrf())
                        .param("payeeVpa", vpaB).param("amount", "750").param("note", "Movie").param("pin", "246810"))
                .andExpect(redirectedUrl("/upi/success"));
        assertThat(accounts.balanceOf(b)).isEqualByComparingTo("750");

        mvc.perform(get("/upi/receive").session(sa)).andExpect(content().string(containsString("<svg")));
        mvc.perform(post("/upi/balance").session(sa).with(csrf()).param("pin", "246810"))
                .andExpect(content().string(containsString("₹4,250.00")));
        mvc.perform(get("/upi/history").session(sa)).andExpect(content().string(containsString("Movie")));
        // The payment also shows in the passbook
        mvc.perform(get("/customer/passbook").session(sb)).andExpect(content().string(containsString("UPI/" + vpaA + "/Movie")));
    }

    @Test
    void wrongAtmPinShowsAnErrorAndPendingAccountsCannotActivate() throws Exception {
        OpenedAccount a = accounts.active(0);
        String wrong = a.pin().equals("0000") ? "1111" : "0000";
        mvc.perform(post("/upi/register").session(login(a)).with(csrf())
                        .param("atmPin", wrong).param("upiPin", "246810").param("confirmPin", "246810"))
                .andExpect(view().name("upi/register"))
                .andExpect(model().attribute("error", "Wrong PIN. 2 attempts left"));

        OpenedAccount pending = accounts.pending();
        mvc.perform(post("/upi/register").session(login(pending)).with(csrf())
                        .param("atmPin", pending.pin()).param("upiPin", "246810").param("confirmPin", "246810"))
                .andExpect(model().attribute("error", containsString("approval")));
    }
}
