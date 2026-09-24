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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpiWebFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;

    private String activate(OpenedAccount a) throws Exception {
        MvcResult r = mvc.perform(post("/upi/register").with(csrf()).param("cardNumber", a.cardNumber())
                        .param("atmPin", a.pin()).param("upiPin", "246810").param("confirmPin", "246810"))
                .andExpect(redirectedUrl("/upi/login")).andReturn();
        return (String) r.getFlashMap().get("registeredVpa");
    }

    private MockHttpSession login(String vpa) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/upi/login").session(session).with(csrf()).param("vpa", vpa).param("pin", "246810"))
                .andExpect(redirectedUrl("/upi"));
        return session;
    }

    @Test
    void activateLoginPayAndReceive() throws Exception {
        OpenedAccount a = accounts.active(5_000);
        OpenedAccount b = accounts.active(0);
        String vpaA = activate(a);
        String vpaB = activate(b);

        mvc.perform(get("/upi")).andExpect(redirectedUrl("/upi/login"));
        MockHttpSession session = login(vpaA);
        mvc.perform(get("/upi").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString(vpaA)));

        mvc.perform(post("/upi/pay").session(session).with(csrf())
                        .param("payeeVpa", vpaB).param("amount", "750").param("note", "Movie"))
                .andExpect(view().name("upi/confirm"))
                .andExpect(content().string(containsString("TEST CUSTOMER")));

        mvc.perform(post("/upi/pay/confirm").session(session).with(csrf())
                        .param("payeeVpa", vpaB).param("amount", "750").param("note", "Movie").param("pin", "000000"))
                .andExpect(view().name("upi/confirm"))
                .andExpect(model().attribute("error", containsString("2 attempts left")));

        mvc.perform(post("/upi/pay/confirm").session(session).with(csrf())
                        .param("payeeVpa", vpaB).param("amount", "750").param("note", "Movie").param("pin", "246810"))
                .andExpect(redirectedUrl("/upi/success"));
        assertThat(accounts.balanceOf(b)).isEqualByComparingTo("750");

        mvc.perform(get("/upi/receive").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")));
        mvc.perform(post("/upi/balance").session(session).with(csrf()).param("pin", "246810"))
                .andExpect(content().string(containsString("₹4,250.00")));
        mvc.perform(get("/upi/history").session(session)).andExpect(content().string(containsString("Movie")));
    }

    @Test
    void wrongCardDetailsShowAnError() throws Exception {
        mvc.perform(post("/upi/register").with(csrf()).param("cardNumber", "5040930000000000")
                        .param("atmPin", "1234").param("upiPin", "246810").param("confirmPin", "246810"))
                .andExpect(view().name("upi/register"))
                .andExpect(model().attribute("error", "Invalid card number or PIN"));
    }
}
