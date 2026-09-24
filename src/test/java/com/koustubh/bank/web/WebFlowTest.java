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
    @Autowired com.koustubh.bank.service.CustomerService customerService;

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
                .andExpect(model().attributeHasFieldErrors("signupForm", "fullName", "pincode", "mobile"))
                .andExpect(content().string(containsString("Enter your full name")));
        mvc.perform(post("/signup/personal").session(session).with(csrf())
                        .param("fullName", "Web Customer").param("fatherName", "Web Father")
                        .param("dateOfBirth", "2000-01-15").param("gender", "Female")
                        .param("email", "web@example.com").param("mobile", "9000011111").param("maritalStatus", "Unmarried")
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
    void investCheckoutNeedsKycAndOtpThenShowsOnDashboard() throws Exception {
        OpenedAccount a = accounts.active(40_000);
        MockHttpSession session = customer(a);
        var c = customerService.overview(a.customerId()).customer();

        mvc.perform(get("/customer/invest").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Parag Parikh Flexi Cap")));
        mvc.perform(get("/customer/invest/funds/122639").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("NAV over the last 12 months")));

        mvc.perform(post("/customer/invest/order").session(session).with(csrf())
                        .param("type", "SIP").param("schemeCode", "122639").param("amount", "2000"))
                .andExpect(redirectedUrl("/customer/invest/checkout"));
        // Paying before KYC is not possible
        mvc.perform(post("/customer/invest/checkout/pay").session(session).with(csrf()).param("method", "ACCOUNT"))
                .andExpect(redirectedUrl("/customer/invest/checkout"));
        // Every KYC mismatch is reported together
        mvc.perform(post("/customer/invest/checkout/kyc").session(session).with(csrf())
                        .param("pan", "WRONG1234X").param("aadhaar", "000000000000").param("mobile", "9000000000"))
                .andExpect(flash().attribute("kycErrors", org.hamcrest.Matchers.hasSize(3)));
        mvc.perform(post("/customer/invest/checkout/kyc").session(session).with(csrf())
                        .param("pan", c.getPan().toLowerCase()).param("aadhaar", c.getAadhaar()).param("mobile", "+91 " + c.getMobile()))
                .andExpect(redirectedUrl("/customer/invest/checkout#payment"));

        String otp = (String) mvc.perform(post("/customer/invest/checkout/otp").session(session).with(csrf()))
                .andReturn().getFlashMap().get("smsOtp");
        mvc.perform(post("/customer/invest/checkout/pay").session(session).with(csrf()).param("method", "ACCOUNT").param("otp", "000000".equals(otp) ? "111111" : "000000"))
                .andExpect(flash().attribute("payError", containsString("Incorrect OTP")));
        mvc.perform(post("/customer/invest/checkout/pay").session(session).with(csrf()).param("method", "ACCOUNT").param("otp", otp))
                .andExpect(redirectedUrl("/customer/invest/done"));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("38000");

        mvc.perform(get("/customer").session(session))
                .andExpect(content().string(containsString("Parag Parikh")))
                .andExpect(content().string(containsString("Current value")));
        mvc.perform(get("/customer/passbook").session(session)).andExpect(content().string(containsString("SIP Instalment")));
    }

    @Test
    void chartAndLiveEndpointsReturnJsonOnlyForTheOwner() throws Exception {
        OpenedAccount a = accounts.active(10_000);
        MockHttpSession session = customer(a);
        mvc.perform(post("/customer/invest/order").session(session).with(csrf()).param("type", "LUMPSUM").param("schemeCode", "120716").param("amount", "3000"));
        var c = customerService.overview(a.customerId()).customer();
        mvc.perform(post("/customer/invest/checkout/kyc").session(session).with(csrf())
                .param("pan", c.getPan()).param("aadhaar", c.getAadhaar()).param("mobile", c.getMobile()));
        String otp = (String) mvc.perform(post("/customer/invest/checkout/otp").session(session).with(csrf())).andReturn().getFlashMap().get("smsOtp");
        mvc.perform(post("/customer/invest/checkout/pay").session(session).with(csrf()).param("method", "ACCOUNT").param("otp", otp));

        mvc.perform(get("/customer/invest/api/portfolio").param("range", "M1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[-1:].invested").value(org.hamcrest.Matchers.hasItem(3000.0)));
        mvc.perform(get("/customer/invest/api/funds/120716").param("range", "M6").session(session))
                .andExpect(jsonPath("$[-1:].value").value(org.hamcrest.Matchers.hasItem(100.0)));
        mvc.perform(get("/customer/invest/api/live").session(session))
                .andExpect(jsonPath("$.value").value(3000.0))
                .andExpect(jsonPath("$.holdings[0].change1d").exists());
        mvc.perform(get("/customer/invest/api/funds/999").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/customer/invest/api/portfolio").param("range", "10Y").session(session)).andExpect(status().isBadRequest());

        Long holdingId = com.jayway.jsonpath.JsonPath.<Integer>read(mvc.perform(get("/customer/invest/api/live").session(session))
                .andReturn().getResponse().getContentAsString(), "$.holdings[0].id").longValue();
        mvc.perform(get("/customer/invest/api/holdings/" + holdingId).session(session)).andExpect(status().isOk());
        mvc.perform(get("/customer/invest/api/holdings/" + holdingId).session(customer(accounts.active(0)))).andExpect(status().isNotFound());
        mvc.perform(get("/customer/invest/api/live")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/customer/invest").session(session)).andExpect(content().string(containsString("data-chart")));
    }

    @Test
    void insuranceCallbackIsHandledByStaffAndPolicyOpensForTheCustomer() throws Exception {
        OpenedAccount a = accounts.active(30_000);
        MockHttpSession session = customer(a);
        mvc.perform(get("/customer/insurance").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Talk to an expert")));
        mvc.perform(get("/customer/insurance/apply/HEALTH").session(session)).andExpect(status().isOk());
        // All problems at once, and what was typed is kept
        mvc.perform(post("/customer/insurance/apply/HEALTH").session(session).with(csrf())
                        .param("contactName", "Asha").param("mobile", "123").param("city", "").param("age", "12"))
                .andExpect(flash().attribute("errors", org.hamcrest.Matchers.hasSize(org.hamcrest.Matchers.greaterThan(3))));
        MvcResult ok = mvc.perform(post("/customer/insurance/apply/HEALTH").session(session).with(csrf())
                        .param("cover", "1000000").param("contactName", "Asha Rao").param("mobile", "9812345678")
                        .param("city", "Pune").param("age", "34").param("extra", "Self, husband")
                        .param("preferredTime", "Evening (4–8)"))
                .andExpect(redirectedUrl("/customer/insurance/requested")).andReturn();
        mvc.perform(get("/customer/insurance/requested").session(session).flashAttrs(ok.getFlashMap()))
                .andExpect(content().string(containsString("within 24 hours")));

        MockHttpSession staff = staff();
        String queue = mvc.perform(get("/admin/insurance").session(staff)).andExpect(content().string(containsString("Asha Rao")))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("/admin/insurance/(\\d+)/issue").matcher(queue);
        assertThat(m.find()).isTrue();
        mvc.perform(post("/admin/insurance/" + m.group(1) + "/issue").session(staff).with(csrf())
                        .param("insurer", "Demo General Insurance").param("policyNumber", "DGI/T/9001")
                        .param("cover", "1000000").param("premium", "11000"))
                .andExpect(flash().attribute("message", containsString("Policy issued")));
        assertThat(accounts.balanceOf(a)).isEqualByComparingTo("19000");

        String page = mvc.perform(get("/customer/insurance").session(session)).andExpect(content().string(containsString("DGI/T/9001")))
                .andReturn().getResponse().getContentAsString();
        Matcher pm = Pattern.compile("/customer/insurance/policies/(\\d+)").matcher(page);
        assertThat(pm.find()).isTrue();
        mvc.perform(get("/customer/insurance/policies/" + pm.group(1)).session(session))
                .andExpect(content().string(containsString("Demo General Insurance")));
    }

    @Test
    void newStaffMustChangeTheirTemporaryPasswordAndOfficersCantManageStaff() throws Exception {
        MockHttpSession admin = staff();
        MvcResult created = mvc.perform(post("/admin/staff").session(admin).with(csrf())
                        .param("fullName", "Kiran Joshi").param("username", "kiran.j").param("role", "OFFICER"))
                .andReturn();
        String temp = (String) created.getFlashMap().get("tempPassword");

        MockHttpSession kiran = new MockHttpSession();
        mvc.perform(post("/admin/login").session(kiran).with(csrf()).param("username", "kiran.j").param("password", temp))
                .andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/admin").session(kiran)).andExpect(redirectedUrl("/admin/password"));
        mvc.perform(post("/admin/password").session(kiran).with(csrf()).param("currentPassword", temp)
                        .param("newPassword", "KiranBank1").param("confirmPassword", "KiranBank1"))
                .andExpect(redirectedUrl("/admin"));
        mvc.perform(get("/admin").session(kiran)).andExpect(status().isOk());
        mvc.perform(get("/admin/staff").session(kiran)).andExpect(status().isForbidden());

        // A disabled staff member can't log in
        String staffPage = mvc.perform(get("/admin/staff").session(admin)).andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("/admin/staff/(\\d+)/active").matcher(staffPage.substring(staffPage.indexOf("<td class=\"mono\">kiran.j</td>")));
        assertThat(m.find()).isTrue();
        mvc.perform(post("/admin/staff/" + m.group(1) + "/active").session(admin).with(csrf()).param("active", "false"))
                .andExpect(flash().attribute("message", "Login disabled"));
        mvc.perform(post("/admin/login").with(csrf()).param("username", "kiran.j").param("password", "KiranBank1"))
                .andExpect(redirectedUrl("/login?as=staff&error"));
    }

    @Test
    void signedInPagesAreNotCachedAndLogoutClearsTheBrowserCache() throws Exception {
        OpenedAccount a = accounts.active(0);
        MockHttpSession session = customer(a);
        mvc.perform(get("/customer/profile").session(session))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        // Swiping back to /login while signed in goes back into the account
        mvc.perform(get("/login").session(session)).andExpect(redirectedUrl("/customer"));
        // Browsers only accept Clear-Site-Data over HTTPS, so Spring only sends it on secure requests
        mvc.perform(post("/customer/logout").secure(true).session(session).with(csrf()))
                .andExpect(header().string("Clear-Site-Data", containsString("cache")));
        mvc.perform(get("/customer/profile").session(session)).andExpect(redirectedUrl("/login"));
    }

    @Test
    void trackingWorksWithCustomerIdAndDuplicatesAreCaughtOnTheirPage() throws Exception {
        OpenedAccount a = accounts.pending();
        mvc.perform(post("/signup/status").with(csrf()).param("accountNumber", a.customerId().toLowerCase()).param("pan", TestAccounts.lastPan()))
                .andExpect(content().string(containsString("Verification by bank staff")));

        var existing = customerService.overview(a.customerId()).customer();
        MockHttpSession signup = new MockHttpSession();
        mvc.perform(post("/signup/personal").session(signup).with(csrf())
                        .param("fullName", "Dup Person").param("fatherName", "F").param("dateOfBirth", "1990-01-01")
                        .param("gender", "Male").param("email", "dup@example.com").param("mobile", existing.getMobile())
                        .param("maritalStatus", "Married").param("address", "1 Road").param("city", "Pune")
                        .param("state", "MH").param("pincode", "411001").param("country", "India"))
                .andExpect(view().name("signup/personal"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "mobile"))
                .andExpect(content().string(containsString("already registered")));
        mvc.perform(post("/signup/personal").session(signup).with(csrf())
                        .param("fullName", "Dup Person").param("fatherName", "F").param("dateOfBirth", "1990-01-01")
                        .param("gender", "Male").param("email", "dup@example.com").param("mobile", "9123400001")
                        .param("maritalStatus", "Married").param("address", "1 Road").param("city", "Pune")
                        .param("state", "MH").param("pincode", "411001").param("country", "India"))
                .andExpect(redirectedUrl("/signup/additional"));
        // PAN and Aadhaar already used: both errors on page 2, together with the missing documents
        mvc.perform(multipart("/signup/additional").session(signup).with(csrf())
                        .param("religion", "Hindu").param("category", "General").param("income", "None")
                        .param("education", "Graduate").param("occupation", "Student")
                        .param("pan", existing.getPan()).param("aadhaar", existing.getAadhaar())
                        .param("seniorCitizen", "false").param("existingAccount", "false"))
                .andExpect(model().attributeHasFieldErrors("signupForm", "pan", "aadhaar", "panDocument", "aadhaarDocument"))
                .andExpect(content().string(containsString("Please fix these 4 problems")));
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
                .andExpect(flash().attribute("message", containsString("Application declined")));

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
                .param("gender", "Male").param("email", "d@example.com").param("mobile", "9000022222").param("maritalStatus", "Married")
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
