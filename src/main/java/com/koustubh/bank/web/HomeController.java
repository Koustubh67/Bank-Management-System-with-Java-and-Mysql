package com.koustubh.bank.web;

import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.AdminService;
import com.koustubh.bank.service.CustomerLoginService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final AdminService admin;
    private final CustomerLoginService customerLogin;

    public HomeController(AdminService admin, CustomerLoginService customerLogin) {
        this.admin = admin;
        this.customerLogin = customerLogin;
    }

    /** The home page is branding only; it shows live numbers from the database, not made-up figures. */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("stats", admin.dashboard());
        return "index";
    }

    /** The only login page: Customer (Customer ID + password) or Bank staff (username + password). */
    @GetMapping("/login")
    public String login(@RequestParam(required = false) String as, @RequestParam(required = false) String error,
                        HttpSession session, Model model) {
        boolean staff = "staff".equals(as);
        // Back-swiping to the login page while still signed in takes you back into your account
        if (signedIn(session, "CUSTOMER_SECURITY_CONTEXT") && !staff) {
            return "redirect:/customer";
        }
        if (signedIn(session, "STAFF_SECURITY_CONTEXT") && staff) {
            return "redirect:/admin";
        }
        model.addAttribute("staff", staff);
        // Came here from "Invest now" on the public Invest page
        if (session.getAttribute("SPRING_SECURITY_SAVED_REQUEST") instanceof SavedRequest saved
                && saved.getRedirectUrl().contains("/customer/invest")) {
            model.addAttribute("investNext", true);
        }
        if (error != null) {
            Object ex = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            // Staff get a generic message; customers get the attempts-left or locked message.
            model.addAttribute("error", !staff && ex instanceof AuthenticationException ae
                    ? ae.getMessage() : "Invalid username or password");
        }
        return "login";
    }

    private static boolean signedIn(HttpSession session, String key) {
        return session.getAttribute(key) instanceof SecurityContext ctx && ctx.getAuthentication() != null
                && ctx.getAuthentication().isAuthenticated();
    }

    @GetMapping("/login/setup")
    public String setupForm() {
        return "login-setup";
    }

    /** First-time or forgotten password: prove who you are with the debit card and ATM PIN. */
    @PostMapping("/login/setup")
    public String setup(@RequestParam String cardNumber, @RequestParam String atmPin, @RequestParam String password,
                        @RequestParam String confirmPassword, Model model, RedirectAttributes redirect) {
        try {
            if (!password.equals(confirmPassword)) {
                model.addAttribute("error", "Passwords do not match");
                return "login-setup";
            }
            String customerId = customerLogin.resetWithCard(cardNumber.replaceAll("\\s", ""), atmPin, password);
            redirect.addFlashAttribute("setupCustomerId", customerId);
            return "redirect:/login";
        } catch (BankException | AuthenticationException e) {
            model.addAttribute("error", e.getMessage());
            return "login-setup";
        }
    }
}
