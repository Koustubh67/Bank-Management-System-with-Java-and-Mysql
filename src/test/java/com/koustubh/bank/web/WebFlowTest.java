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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Drives the real pages end to end: signup, ATM login and screens, staff approval. */
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

    @Test
    void publicPagesAreOpen() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Open an account")));
        mvc.perform(get("/atm/login")).andExpect(status().isOk());
        mvc.perform(get("/admin/login")).andExpect(status().isOk());
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ATM login")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Log in to staff panel")));
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

        // Without documents page 2 is shown again with upload errors
        mvc.perform(multipart("/signup/additional").session(session).with(csrf())
                        .param("religion", "Hindu").param("category", "General").param("income", "None")
                        .param("education", "Graduate").param("occupation", "Student")
                        .param("pan", "PQRST9876Z").param("aadhaar", "987654321098")
                        .param("seniorCitizen", "false").param("existingAccount", "false"))
                .andExpect(view().name("signup/additional"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "panDocument", "aadhaarDocument"));

        // A renamed HTML file is refused even though it claims to be a PNG
        mvc.perform(multipart("/signup/additional").file(new MockMultipartFile("panFile", "pan.png", "image/png", "<html>".getBytes()))
                        .file(AADHAAR_PDF).session(session).with(csrf())
                        .param("religion", "Hindu").param("category", "General").param("income", "None")
                        .param("education", "Graduate").param("occupation", "Student")
                        .param("pan", "PQRST9876Z").param("aadhaar", "987654321098")
                        .param("seniorCitizen", "false").param("existingAccount", "false"))
                .andExpect(view().name("signup/additional"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "panDocument"));

        // The Aadhaar file was accepted above and kept in the session, so only the PAN is uploaded again
        mvc.perform(multipart("/signup/additional").file(PAN_PNG).session(session).with(csrf())
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
    void staffSeesKycDocumentsAndCanDeclineAndCustomerCanTrackIt() throws Exception {
        MockHttpSession signup = new MockHttpSession();
        mvc.perform(post("/signup/personal").session(signup).with(csrf())
                .param("fullName", "Decline Me").param("fatherName", "Father").param("dateOfBirth", "1995-02-02")
                .param("gender", "Male").param("email", "d@example.com").param("maritalStatus", "Married")
                .param("address", "2 Road").param("city", "Pune").param("state", "MH").param("pincode", "411001")
                .param("country", "India"));
        mvc.perform(multipart("/signup/additional").file(PAN_PNG).file(AADHAAR_PDF).session(signup).with(csrf())
                .param("religion", "Hindu").param("category", "General").param("income", "None")
                .param("education", "Graduate").param("occupation", "Student")
                .param("pan", "DECLN1234D").param("aadhaar", "111122223333")
                .param("seniorCitizen", "false").param("existingAccount", "false"))
                .andExpect(redirectedUrl("/signup/account"));
        MvcResult done = mvc.perform(post("/signup/account").session(signup).with(csrf())
                .param("accountType", "SAVINGS").param("declaration", "true")).andReturn();
        OpenedAccount opened = (OpenedAccount) done.getFlashMap().get("opened");
        Long id = accounts.idOf(opened);

        mvc.perform(post("/signup/status").with(csrf()).param("accountNumber", opened.accountNumber()).param("pan", "decln1234d"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Verification by bank staff")));
        mvc.perform(post("/signup/status").with(csrf()).param("accountNumber", opened.accountNumber()).param("pan", "WRONG1234X"))
                .andExpect(model().attribute("error", "No application found with this account number and PAN"));

        MockHttpSession staff = new MockHttpSession();
        mvc.perform(post("/admin/login").session(staff).with(csrf()).param("username", "admin").param("password", "admin123"));
        String page = mvc.perform(get("/admin/accounts/" + id).session(staff))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("my_pan.png")))
                .andReturn().getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("href=\"(/admin/documents/\\d+)\"").matcher(page);
        assertThat(m.find()).isTrue();
        String docUrl = m.group(1);
        mvc.perform(get(docUrl).session(staff)).andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get(docUrl)).andExpect(redirectedUrl("/admin/login"));

        mvc.perform(post("/admin/accounts/" + id + "/decline").session(staff).with(csrf())
                        .param("reason", "").param("note", ""))
                .andExpect(flash().attribute("error", "Please give a reason for declining"));
        mvc.perform(post("/admin/accounts/" + id + "/decline").session(staff).with(csrf())
                        .param("reason", "PAN card image is unclear or unreadable").param("note", "Please upload the front side"))
                .andExpect(flash().attribute("message", "Application declined"));

        mvc.perform(post("/signup/status").with(csrf()).param("accountNumber", opened.accountNumber()).param("pan", "DECLN1234D"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "PAN card image is unclear or unreadable. Please upload the front side")));
        mvc.perform(post("/atm/login").with(csrf()).param("cardNumber", opened.cardNumber()).param("pin", opened.pin()))
                .andExpect(redirectedUrl("/atm/login?error"));
    }

    @Test
    void staffCanLogInFromTheCombinedLoginPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/login").param("as", "staff").session(session)).andExpect(status().isOk());
        mvc.perform(post("/admin/login").session(session).with(csrf()).param("username", "admin").param("password", "admin123"))
                .andExpect(redirectedUrl("/admin"));
    }

    @Test
    void wrongStaffPasswordIsRejected() throws Exception {
        mvc.perform(post("/admin/login").with(csrf()).param("username", "admin").param("password", "nope"))
                .andExpect(redirectedUrl("/admin/login?error"));
    }
}
