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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Drives the real pages end to end: signup, ATM login and screens, staff approval. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebFlowTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;

    @Test
    void publicPagesAreOpen() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Open an account")));
        mvc.perform(get("/atm/login")).andExpect(status().isOk());
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
    }

    @Test
    void protectedPagesRedirectToTheirLogin() throws Exception {
        mvc.perform(get("/atm")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/atm/login"));
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void signupFlowCreatesAccountAndShowsCredentialsOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mvc.perform(get("/signup/account").session(session)).andExpect(redirectedUrl("/signup/additional"));

        mvc.perform(post("/signup/personal").session(session).with(csrf()).param("fullName", ""))
                .andExpect(status().isOk()).andExpect(view().name("signup/personal"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "fullName", "pincode"));

        mvc.perform(post("/signup/personal").session(session).with(csrf())
                        .param("fullName", "Web Customer").param("fatherName", "Web Father")
                        .param("dateOfBirth", "2000-01-15").param("gender", "Female")
                        .param("email", "web@example.com").param("maritalStatus", "Unmarried")
                        .param("address", "1 Lake Road").param("city", "Bhopal").param("state", "MP")
                        .param("pincode", "462001").param("country", "India"))
                .andExpect(redirectedUrl("/signup/additional"));

        mvc.perform(post("/signup/additional").session(session).with(csrf())
                        .param("religion", "Hindu").param("category", "General").param("income", "None")
                        .param("education", "Graduate").param("occupation", "Student")
                        .param("pan", "pqrst9876z").param("aadhaar", "9876 5432 1098")
                        .param("seniorCitizen", "false").param("existingAccount", "false"))
                .andExpect(redirectedUrl("/signup/account"));

        MvcResult result = mvc.perform(post("/signup/account").session(session).with(csrf())
                        .param("accountType", "SAVINGS").param("services", "ATM Card", "Cheque Book")
                        .param("declaration", "true"))
                .andExpect(redirectedUrl("/signup/done"))
                .andReturn();
        OpenedAccount opened = (OpenedAccount) result.getFlashMap().get("opened");
        assertThat(opened.cardNumber()).hasSize(16);

        mvc.perform(get("/signup/done").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(opened.cardNumber())));
        // Without the one-time flash data the page is not shown again
        mvc.perform(get("/signup/done")).andExpect(redirectedUrl("/"));
    }

    @Test
    void atmLoginAndWithdrawal() throws Exception {
        OpenedAccount a = accounts.active(3_000);
        String wrong = a.pin().equals("0000") ? "1111" : "0000";

        MockHttpSession failed = new MockHttpSession();
        mvc.perform(post("/atm/login").session(failed).with(csrf()).param("cardNumber", a.cardNumber()).param("pin", wrong))
                .andExpect(redirectedUrl("/atm/login?error"));
        mvc.perform(get("/atm/login").session(failed).param("error", ""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2 attempts left")));

        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/atm/login").session(session).with(csrf()).param("cardNumber", a.cardNumber()).param("pin", a.pin()))
                .andExpect(redirectedUrl("/atm"));
        mvc.perform(get("/atm").session(session)).andExpect(status().isOk()).andExpect(view().name("atm/menu"));

        mvc.perform(post("/atm/withdraw").session(session).with(csrf()).param("amount", "150"))
                .andExpect(view().name("atm/withdraw"))
                .andExpect(model().attribute("error", "Amount must be a multiple of Rs 100"));

        mvc.perform(post("/atm/withdraw").session(session).with(csrf()).param("amount", "1000"))
                .andExpect(redirectedUrl("/atm/receipt"));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("2000");

        mvc.perform(get("/atm/statement").session(session)).andExpect(status().isOk());
        // A customer login does not give access to the staff panel
        mvc.perform(get("/admin").session(session)).andExpect(redirectedUrl("/admin/login"));
    }

    @Test
    void staffCanApproveAccount() throws Exception {
        OpenedAccount a = accounts.pending();
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin/login").session(session).with(csrf()).param("username", "admin").param("password", "admin123"))
                .andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(a.accountNumber())));

        Long id = accounts.idOf(a);
        mvc.perform(post("/admin/accounts/" + id + "/approve").session(session).with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts/" + id));
        mvc.perform(get("/admin/accounts/" + id).session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ACTIVE")));
    }

    @Test
    void wrongStaffPasswordIsRejected() throws Exception {
        mvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", "nope"))
                .andExpect(redirectedUrl("/admin/login?error"));
    }
}
