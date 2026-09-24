package com.koustubh.bank.web;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.service.EmiCalculator;
import com.koustubh.bank.service.OpenedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The full loan journey through the real pages: public enquiry, customer application, staff decision, EMIs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanWebTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;

    private MockHttpSession customer(OpenedAccount a) throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/customer/login").session(s).with(csrf()).param("customerId", a.customerId()).param("password", TestAccounts.PASSWORD));
        return s;
    }

    private MockHttpSession staff() throws Exception {
        MockHttpSession s = new MockHttpSession();
        mvc.perform(post("/admin/login").session(s).with(csrf()).param("username", "admin").param("password", "admin123"));
        return s;
    }

    @Test
    void firstTimeVisitorsGetTheWholePageWithItsFormToken() throws Exception {
        // No session yet: the CSRF token (and session) must exist before the page starts rendering
        MvcResult r = mvc.perform(get("/loans")).andExpect(status().isOk()).andReturn();
        assertThat(r.getRequest().getSession(false)).isNotNull();
        assertThat(r.getResponse().getContentAsString()).contains("name=\"_csrf\"").contains("</html>");
    }

    @Test
    void publicCalculatorAndEnquiryWithoutLogin() throws Exception {
        mvc.perform(get("/loans")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Home loan")))
                .andExpect(content().string(containsString("Request a call back")));
        MvcResult bad = mvc.perform(post("/loans/enquiry").with(csrf()).param("name", "Ravi").param("mobile", "12"))
                .andExpect(flash().attribute("enquiryErrors", hasSize(org.hamcrest.Matchers.greaterThan(3)))).andReturn();
        mvc.perform(get("/loans").flashAttrs(bad.getFlashMap())).andExpect(content().string(containsString("value=\"Ravi\"")));

        MvcResult ok = mvc.perform(post("/loans/enquiry").with(csrf()).param("name", "Ravi Kumar").param("mobile", "9876501234")
                        .param("email", "ravi@example.com").param("city", "Indore").param("type", "CAR").param("amount", "700000")
                        .param("employment", "Salaried").param("monthlyIncome", "75000").param("preferredTime", "Morning (9–12)"))
                .andExpect(redirectedUrl("/loans/thanks")).andReturn();
        mvc.perform(get("/loans/thanks").flashAttrs(ok.getFlashMap()))
                .andExpect(content().string(containsString("within 24 hours")))
                .andExpect(content().string(containsString("JBE")));
        mvc.perform(get("/admin/loans").session(staff())).andExpect(content().string(containsString("Ravi Kumar")));
    }

    @Test
    void customerAppliesStaffApprovesAndBothSeeEmiDatesAndEndDate() throws Exception {
        OpenedAccount a = accounts.active(2_000);
        MockHttpSession session = customer(a);
        mvc.perform(get("/customer/loans").session(session)).andExpect(status().isOk());
        mvc.perform(get("/customer/loans/apply").param("type", "CAR").session(session)).andExpect(status().isOk());
        // Missing declaration and tenure: errors shown, values kept
        mvc.perform(post("/customer/loans/apply").session(session).with(csrf()).param("type", "CAR").param("amount", "500000")
                        .param("purpose", "Car").param("employment", "Salaried").param("monthlyIncome", "90000"))
                .andExpect(redirectedUrl("/customer/loans/apply"))
                .andExpect(flash().attributeExists("errors"));
        mvc.perform(post("/customer/loans/apply").session(session).with(csrf()).param("type", "CAR").param("amount", "500000")
                        .param("months", "48").param("purpose", "Maruti Brezza").param("employment", "Salaried")
                        .param("monthlyIncome", "90000").param("declaration", "true"))
                .andExpect(redirectedUrl("/customer/loans/applied"));
        String list = mvc.perform(get("/customer/loans").session(session)).andExpect(content().string(containsString("UNDER REVIEW")))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("/customer/loans/(\\d+)\"").matcher(list);
        assertThat(m.find()).isTrue();
        String loanId = m.group(1);

        String firstEmi = EmiCalculator.firstEmiDate(LocalDate.now()).format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String endDate = EmiCalculator.firstEmiDate(LocalDate.now()).plusMonths(47).format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        MockHttpSession staff = staff();
        mvc.perform(get("/admin/loans").session(staff)).andExpect(content().string(containsString("Maruti Brezza")));
        // Before deciding, the officer sees when the EMIs would start and when the loan would end
        mvc.perform(get("/admin/loans/" + loanId).session(staff))
                .andExpect(content().string(containsString("Approve &amp; disburse")))
                .andExpect(content().string(containsString("first on <b>" + firstEmi)))
                .andExpect(content().string(containsString(endDate)));
        mvc.perform(post("/admin/loans/" + loanId + "/approve").session(staff).with(csrf())
                        .param("principal", "450000").param("rate", "8.75").param("months", "48"))
                .andExpect(flash().attribute("message", containsString("disbursed")));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("452000");

        mvc.perform(get("/customer/loans/" + loanId).session(session))
                .andExpect(content().string(containsString(firstEmi)))
                .andExpect(content().string(containsString(endDate)))
                .andExpect(content().string(containsString("EMI schedule")));
        mvc.perform(get("/admin/loans/" + loanId).session(staff))
                .andExpect(content().string(containsString(firstEmi)))
                .andExpect(content().string(containsString(endDate)));
        mvc.perform(get("/customer").session(session)).andExpect(content().string(containsString("My loans")));

        mvc.perform(post("/customer/loans/" + loanId + "/pay").session(session).with(csrf()))
                .andExpect(flash().attribute("message", containsString("EMI 1")));
        // Decided loans can't be approved again from the page
        mvc.perform(post("/admin/loans/" + loanId + "/approve").session(staff).with(csrf())
                        .param("principal", "450000").param("rate", "8.75").param("months", "48"))
                .andExpect(flash().attribute("error", containsString("already")));

        // Someone else can't see it; loan pages need the right login
        mvc.perform(get("/customer/loans/" + loanId).session(customer(accounts.active(0)))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/loans")).andExpect(redirectedUrl("/login?as=staff"));
        mvc.perform(get("/customer/loans")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void staffCanRejectWithAReasonTheCustomerSees() throws Exception {
        OpenedAccount a = accounts.active(0);
        MockHttpSession session = customer(a);
        mvc.perform(post("/customer/loans/apply").session(session).with(csrf()).param("type", "PERSONAL").param("amount", "200000")
                .param("months", "24").param("purpose", "Wedding").param("employment", "Salaried")
                .param("monthlyIncome", "60000").param("declaration", "true"));
        String list = mvc.perform(get("/customer/loans").session(session)).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("/customer/loans/(\\d+)\"").matcher(list);
        assertThat(m.find()).isTrue();
        MockHttpSession staff = staff();
        mvc.perform(post("/admin/loans/" + m.group(1) + "/reject").session(staff).with(csrf()).param("reason", ""))
                .andExpect(flash().attribute("error", containsString("reason")));
        mvc.perform(post("/admin/loans/" + m.group(1) + "/reject").session(staff).with(csrf()).param("reason", "Credit score below 650"))
                .andExpect(flash().attribute("message", containsString("rejected")));
        mvc.perform(get("/customer/loans/" + m.group(1)).session(session)).andExpect(content().string(containsString("Credit score below 650")));
    }
}
