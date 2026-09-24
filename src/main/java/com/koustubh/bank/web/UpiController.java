package com.koustubh.bank.web;

import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.UpiService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;

/** JavaPay UPI screens. The logged-in principal's name is the UPI ID. */
@Controller
@RequestMapping("/upi")
public class UpiController {

    private final UpiService upi;
    private final QrCodes qrCodes;

    public UpiController(UpiService upi, QrCodes qrCodes) {
        this.upi = upi;
        this.qrCodes = qrCodes;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, HttpSession session, Model model) {
        if (error != null) {
            Object ex = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            model.addAttribute("error", ex instanceof AuthenticationException ae ? ae.getMessage() : "Login failed");
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
        return "upi/login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "upi/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String cardNumber, @RequestParam String atmPin, @RequestParam String upiPin,
                           @RequestParam String confirmPin, Model model, RedirectAttributes redirect) {
        try {
            if (!upiPin.equals(confirmPin)) {
                throw new InvalidRequestException("UPI PINs do not match");
            }
            String vpa = upi.register(cardNumber.replaceAll("\\s", ""), atmPin, upiPin);
            redirect.addFlashAttribute("registeredVpa", vpa);
            return "redirect:/upi/login";
        } catch (BankException | AuthenticationException e) {
            model.addAttribute("error", e.getMessage());
            return "upi/register";
        }
    }

    @GetMapping
    public String home(Principal principal, Model model) {
        model.addAttribute("profile", upi.profile(principal.getName()));
        model.addAttribute("transactions", upi.history(principal.getName()).stream().limit(5).toList());
        return "upi/home";
    }

    @GetMapping("/pay")
    public String payForm(@RequestParam(required = false) String pa, Model model) {
        model.addAttribute("payeeVpa", pa);
        return "upi/pay";
    }

    /** Step 1: check the UPI ID and amount, then show the payee's registered name and ask for the UPI PIN. */
    @PostMapping("/pay")
    public String review(@RequestParam String payeeVpa, @RequestParam(required = false) String amount,
                         @RequestParam(required = false) String note, Principal principal, Model model) {
        model.addAttribute("payeeVpa", payeeVpa);
        model.addAttribute("amount", amount);
        model.addAttribute("note", note);
        try {
            BigDecimal value = parseAmount(amount);
            model.addAttribute("payee", upi.lookup(principal.getName(), payeeVpa));
            model.addAttribute("amountValue", value);
            return "upi/confirm";
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
            return "upi/pay";
        }
    }

    /** Step 2: the customer entered the UPI PIN. */
    @PostMapping("/pay/confirm")
    public String pay(@RequestParam String payeeVpa, @RequestParam String amount,
                      @RequestParam(required = false) String note, @RequestParam(required = false) String pin,
                      Principal principal, Model model, RedirectAttributes redirect) {
        try {
            var t = upi.pay(principal.getName(), payeeVpa, parseAmount(amount), note, pin);
            redirect.addFlashAttribute("payment", t);
            redirect.addFlashAttribute("payee", upi.lookup(principal.getName(), payeeVpa));
            return "redirect:/upi/success";
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("payeeVpa", payeeVpa);
            model.addAttribute("amount", amount);
            model.addAttribute("note", note);
            try {
                model.addAttribute("payee", upi.lookup(principal.getName(), payeeVpa));
                model.addAttribute("amountValue", parseAmount(amount));
                return "upi/confirm";
            } catch (BankException again) {
                return "upi/pay";
            }
        }
    }

    @GetMapping("/success")
    public String success(Model model) {
        return model.containsAttribute("payment") ? "upi/success" : "redirect:/upi";
    }

    @GetMapping("/receive")
    public String receive(Principal principal, Model model) {
        model.addAttribute("profile", upi.profile(principal.getName()));
        model.addAttribute("qr", qrCodes.svg(upi.paymentUri(principal.getName())));
        return "upi/receive";
    }

    @GetMapping("/balance")
    public String balanceForm() {
        return "upi/balance";
    }

    @PostMapping("/balance")
    public String balance(@RequestParam(required = false) String pin, Principal principal, Model model) {
        try {
            model.addAttribute("balance", upi.balance(principal.getName(), pin));
            model.addAttribute("profile", upi.profile(principal.getName()));
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "upi/balance";
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        model.addAttribute("transactions", upi.history(principal.getName()));
        return "upi/history";
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("Please enter a valid amount");
        }
    }
}
