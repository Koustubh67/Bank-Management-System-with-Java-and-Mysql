package com.koustubh.bank.web;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.service.OpenedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Drives the real pages end to end: signup with KYC, customer login, dashboard, ATM, staff review. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebFlowTest {

    static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final MockMultipartFile PAN_PNG = new MockMultipartFile("panFile", "my pan.png", "image/png", PNG_BYTES);
    static final MockMultipartFile AADHAAR_PDF =
            new MockMultipartFile("aadhaarFile", "aadhaar.pdf", "application/pdf", "%PDF-1.4 test".getBytes());

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;

    /** Logs a customer in and returns their session. */
    MockHttpSession customer(OpenedAccount a) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/customer/login").session(session).with(csrf())
                        .param("customerId", a.customerId()).param("password", TestAccounts.PASSWORD))
                .andExpect(redirectedUrl("/customer"));
        return session;
    }

    MockHttpSession staff() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin/login").session(session).with(csrf()).param("username", "admin").param("password", "admin123"))
                .andExpect(redirectedUrl("/admin"));
        return session;
    }

    @Test
    void onlyBrandingSignupTrackingAndLoginArePublic() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Trusted by")))
                .andExpect(content().string(not(containsString("href=\"/atm"))))
                .andExpect(content().string(not(containsString("href=\"/upi"))));
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Customer ID")))
                .andExpect(content().string(containsString("Log in to staff panel")));
        mvc.perform(get("/signup/personal")).andExpect(status().isOk());
        mvc.perform(get("/signup/status")).andExpect(status().isOk());
    }

    @Test
    void bankingPagesNeedTheRightLogin() throws Exception {
        for (String url : new String[]{"/customer", "/customer/passbook", "/customer/profile", "/atm", "/atm/login", "/upi", "/upi/pay"}) {
            mvc.perform(get(url)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        }
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/login?as=staff"));
        mvc.perform(get("/admin/login")).andExpect(redirectedUrl("/login?as=staff"));

        // A customer login is not a staff login, and the other way round
        OpenedAccount a = accounts.active(0);
        mvc.perform(get("/admin").session(customer(a))).andExpect(redirectedUrl("/login?as=staff"));
        mvc.perform(get("/customer").session(staff())).andExpect(redirectedUrl("/login"));
    }

    @Test
    void wrongPasswordShowsAttemptsLeft() throws Exception {
        OpenedAccount a = accounts.active(0);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/customer/login").session(session).with(csrf())
                        .param("customerId", a.customerId()).param("password", "WrongPass1"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(get("/login").param("error", "").session(session))
                .andExpect(content().string(containsString("4 attempts left")));
        mvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", "nope"))
                .andExpect(redirectedUrl("/login?as=staff&error"));
    }

    @Test
    void signupFlowCreatesAccountAndShowsCredentialsOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/signup/account").session(session)).andExpect(redirectedUrl("/signup/additional"));
        mvc.perform(post("/signup/personal").session(session).with(csrf()).param("fullName", ""))
                .andExpect(view().name("signup/personal"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "fullName", "pincode"));
        mvc.perform(post("/signup/personal").session(session).with(csrf())
                        .param("fullName", "Web Customer").param("fatherName", "Web Father")
                        .param("dateOfBirth", "2000-01-15").param("gender", "Female")
                        .param("email", "web@example.com").param("maritalStatus", "Unmarried")
                        .param("address", "1 Lake Road").param("city", "Bhopal").param("state", "MP")
                        .param("pincode", "462001").param("country", "India"))
                .andExpect(redirectedUrl("/signup/additional"));

        String[][] page2 = {{"religion", "Hindu"}, {"category", "General"}, {"income", "None"}, {"education", "Graduate"},
                {"occupation", "Student"}, {"pan", "pqrst9876z"}, {"aadhaar", "9876 5432 1098"},
                {"seniorCitizen", "false"}, {"existingAccount", "false"}};
        var noFiles = multipart("/signup/additional").session(session).with(csrf());
        var badPan = multipart("/signup/additional").file(new MockMultipartFile("panFile", "pan.png", "image/png", "<html>".getBytes()))
                .file(AADHAAR_PDF).session(session).with(csrf());
        var goodPan = multipart("/signup/additional").file(PAN_PNG).session(session).with(csrf());
        for (String[] p : page2) {
            noFiles.param(p[0], p[1]);
            badPan.param(p[0], p[1]);
            goodPan.param(p[0], p[1]);
        }
        mvc.perform(noFiles).andExpect(model().attributeHasFieldErrors("signupForm", "panDocument", "aadhaarDocument"));
        // A renamed HTML file is refused even though it claims to be a PNG
        mvc.perform(badPan).andExpect(model().attributeHasFieldErrors("signupForm", "panDocument"));
        // The Aadhaar file was accepted above and kept in the session, so only the PAN is uploaded again
        mvc.perform(goodPan).andExpect(redirectedUrl("/signup/account"));

        mvc.perform(post("/signup/account").session(session).with(csrf())
                        .param("accountType", "SAVINGS").param("declaration", "true")
                        .param("password", "short").param("confirmPassword", "different"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "password", "passwordConfirmed"));
        MvcResult result = mvc.perform(post("/signup/account").session(session).with(csrf())
                        .param("accountType", "SAVINGS").param("services", "ATM Card", "Cheque Book")
                        .param("declaration", "true").param("password", "MyBank2026").param("confirmPassword", "MyBank2026"))
                .andExpect(redirectedUrl("/signup/done"))
                .andReturn();
        OpenedAccount opened = (OpenedAccount) result.getFlashMap().get("opened");
        mvc.perform(get("/signup/done").flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString(opened.customerId())));
        mvc.perform(get("/signup/done")).andExpect(redirectedUrl("/"));

        // The customer can log in straight away and sees that the account is under review
        MockHttpSession login = new MockHttpSession();
        mvc.perform(post("/customer/login").session(login).with(csrf())
                .param("customerId", opened.customerId()).param("password", "MyBank2026")).andExpect(redirectedUrl("/customer"));
        mvc.perform(get("/customer").session(login)).andExpect(content().string(containsString("under review")));
    }

    @Test
    void dashboardPassbookProfileAndAtmAfterLogin() throws Exception {
        OpenedAccount a = accounts.active(3_000);
        MockHttpSession session = customer(a);

        mvc.perform(get("/customer").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString(a.customerId())))
                .andExpect(content().string(containsString("₹3,000.00")));

        // ATM: needs the card PIN in this session before any screen
        mvc.perform(get("/atm/withdraw").session(session)).andExpect(redirectedUrl("/atm/login"));
        String wrong = a.pin().equals("0000") ? "1111" : "0000";
        mvc.perform(post("/atm/insert").session(session).with(csrf()).param("pin", wrong))
                .andExpect(model().attribute("error", "Wrong PIN. 2 attempts left"));
        mvc.perform(post("/atm/insert").session(session).with(csrf()).param("pin", a.pin())).andExpect(redirectedUrl("/atm"));
        mvc.perform(post("/atm/withdraw").session(session).with(csrf()).param("amount", "150"))
                .andExpect(model().attribute("error", "Amount must be a multiple of Rs 100"));
        mvc.perform(post("/atm/withdraw").session(session).with(csrf()).param("amount", "1000"))
                .andExpect(redirectedUrl("/atm/receipt"));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("2000");
        mvc.perform(post("/atm/exit").session(session).with(csrf())).andExpect(redirectedUrl("/customer"));
        mvc.perform(get("/atm").session(session)).andExpect(redirectedUrl("/atm/login"));

        mvc.perform(get("/customer/passbook").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Withdrawal")))
                .andExpect(content().string(containsString("₹2,000.00")));
        mvc.perform(get("/customer/passbook").param("type", "credit").session(session))
                .andExpect(content().string(not(containsString("<b>Withdrawal</b>"))));
        mvc.perform(get("/customer/passbook.csv").session(session)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("Date,Description,Reference,Debit,Credit,Balance")))
                .andExpect(content().string(containsString(",1000.00,,2000.00")));

        mvc.perform(get("/customer/profile").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("XXXX-XXXX-")));
        mvc.perform(post("/customer/password").session(session).with(csrf())
                        .param("currentPassword", TestAccounts.PASSWORD).param("newPassword", "Changed123")
                        .param("confirmPassword", "Changed123"))
                .andExpect(flash().attribute("message", "Your password has been changed"));

        mvc.perform(post("/customer/logout").session(session).with(csrf())).andExpect(redirectedUrl("/login?logout"));
        mvc.perform(get("/customer").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void passwordCanBeSetWithTheDebitCard() throws Exception {
        OpenedAccount a = accounts.active(0);
        mvc.perform(post("/login/setup").with(csrf()).param("cardNumber", a.cardNumber()).param("atmPin", a.pin())
                        .param("password", "FromCard77").param("confirmPassword", "FromCard77"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("setupCustomerId", a.customerId()));
        mvc.perform(post("/customer/login").with(csrf()).param("customerId", a.customerId()).param("password", "FromCard77"))
                .andExpect(redirectedUrl("/customer"));
    }

    @Test
    void staffReviewsKycDeclinesAndCustomerSeesTheReason() throws Exception {
        OpenedAccount opened = accounts.pending();
        Long id = accounts.idOf(opened);
        MockHttpSession staff = staff();

        mvc.perform(get("/admin").session(staff)).andExpect(content().string(containsString(opened.accountNumber())));
        mvc.perform(post("/admin/accounts/" + id + "/decline").session(staff).with(csrf()).param("reason", "").param("note", ""))
                .andExpect(flash().attribute("error", "Please give a reason for declining"));
        mvc.perform(post("/admin/accounts/" + id + "/decline").session(staff).with(csrf())
                        .param("reason", "PAN card image is unclear or unreadable").param("note", "Please upload the front side"))
                .andExpect(flash().attribute("message", "Application declined"));

        String reason = "PAN card image is unclear or unreadable. Please upload the front side";
        mvc.perform(post("/signup/status").with(csrf()).param("accountNumber", opened.accountNumber()).param("pan", TestAccounts.lastPan()))
                .andExpect(content().string(containsString(reason)));
        MockHttpSession session = customer(opened);
        mvc.perform(get("/customer").session(session)).andExpect(content().string(containsString(reason)));
        mvc.perform(post("/atm/insert").session(session).with(csrf()).param("pin", opened.pin()))
                .andExpect(model().attribute("error", containsString("declined")));
    }

    @Test
    void staffCanViewUploadedKycDocuments() throws Exception {
        MockHttpSession signup = new MockHttpSession();
        mvc.perform(post("/signup/personal").session(signup).with(csrf())
                .param("fullName", "Doc Viewer").param("fatherName", "Father").param("dateOfBirth", "1995-02-02")
                .param("gender", "Male").param("email", "d@example.com").param("maritalStatus", "Married")
                .param("address", "2 Road").param("city", "Pune").param("state", "MH").param("pincode", "411001")
                .param("country", "India"));
        mvc.perform(multipart("/signup/additional").file(PAN_PNG).file(AADHAAR_PDF).session(signup).with(csrf())
                .param("religion", "Hindu").param("category", "General").param("income", "None")
                .param("education", "Graduate").param("occupation", "Student")
                .param("pan", "DOCVW1234D").param("aadhaar", "111122223333")
                .param("seniorCitizen", "false").param("existingAccount", "false"))
                .andExpect(redirectedUrl("/signup/account"));
        MvcResult done = mvc.perform(post("/signup/account").session(signup).with(csrf())
                .param("accountType", "SAVINGS").param("declaration", "true")
                .param("password", "Docs12345").param("confirmPassword", "Docs12345")).andReturn();
        OpenedAccount opened = (OpenedAccount) done.getFlashMap().get("opened");

        MockHttpSession staff = staff();
        String page = mvc.perform(get("/admin/accounts/" + accounts.idOf(opened)).session(staff))
                .andExpect(content().string(containsString("my_pan.png")))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("href=\"(/admin/documents/\\d+)\"").matcher(page);
        assertThat(m.find()).isTrue();
        mvc.perform(get(m.group(1)).session(staff)).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(m.group(1))).andExpect(redirectedUrl("/login?as=staff"));
        mvc.perform(get(m.group(1)).session(customer(accounts.active(0)))).andExpect(redirectedUrl("/login?as=staff"));
    }
}
