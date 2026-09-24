package com.koustubh.bank.web;

import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.UpiService;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.function.Function;

/**
 * JavaPay UPI, opened from the customer dashboard. The logged-in principal is the Customer ID; the UPI ID is looked
 * up from it. A customer who hasn't activated UPI yet is sent to the activation screen.
 */
@Controller
@RequestMapping("/upi")
public class UpiController {

    private final UpiService upi;
    private final CustomerService customers;
    private final QrCodes qrCodes;

    public UpiController(UpiService upi, CustomerService customers, QrCodes qrCodes) {
        this.upi = upi;
        this.customers = customers;
        this.qrCodes = qrCodes;
    }

    /** Runs the page with the customer's UPI ID, or redirects to activation if there is none. */
    private String withVpa(Principal principal, Function<String, String> page) {
        return upi.vpaForCustomer(principal.getName()).map(page).orElse("redirect:/upi/register");
    }

    @GetMapping("/register")
    public String registerForm(Principal principal, Model model) {
        model.addAttribute("maskedCard", "XXXX XXXX XXXX " + customers.cardNumber(principal.getName()).substring(12));
        model.addAttribute("existingVpa", upi.vpaForCustomer(principal.getName()).orElse(null));
        return "upi/register";
    }

    /** Activates UPI, or resets a forgotten UPI PIN, using the customer's debit card PIN. */
    @PostMapping("/register")
    public String register(@RequestParam String atmPin, @RequestParam String upiPin, @RequestParam String confirmPin,
                           Principal principal, Model model, RedirectAttributes redirect) {
        String cardNumber = customers.cardNumber(principal.getName());
        try {
            if (!upiPin.equals(confirmPin)) {
                throw new InvalidRequestException("UPI PINs do not match");
            }
            String vpa = upi.register(cardNumber, atmPin, upiPin);
            redirect.addFlashAttribute("message", "UPI is ready. Your UPI ID is " + vpa);
            return "redirect:/upi";
        } catch (BankException | AuthenticationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("maskedCard", "XXXX XXXX XXXX " + cardNumber.substring(12));
            model.addAttribute("existingVpa", upi.vpaForCustomer(principal.getName()).orElse(null));
            return "upi/register";
        }
    }

    @GetMapping
    public String home(Principal principal, Model model) {
        return withVpa(principal, vpa -> {
            model.addAttribute("profile", upi.profile(vpa));
            model.addAttribute("transactions", upi.history(vpa).stream().limit(5).toList());
            return "upi/home";
        });
    }

    @GetMapping("/pay")
    public String payForm(@RequestParam(required = false) String pa, Principal principal, Model model) {
        return withVpa(principal, vpa -> {
            model.addAttribute("payeeVpa", pa);
            return "upi/pay";
        });
    }

    /** Step 1: check the UPI ID and amount, then show the payee's registered name and ask for the UPI PIN. */
    @PostMapping("/pay")
    public String review(@RequestParam String payeeVpa, @RequestParam(required = false) String amount,
                         @RequestParam(required = false) String note, Principal principal, Model model) {
        return withVpa(principal, vpa -> {
            model.addAttribute("payeeVpa", payeeVpa);
            model.addAttribute("amount", amount);
            model.addAttribute("note", note);
            try {
                BigDecimal value = parseAmount(amount);
                model.addAttribute("payee", upi.lookup(vpa, payeeVpa));
                model.addAttribute("amountValue", value);
                return "upi/confirm";
            } catch (BankException e) {
                model.addAttribute("error", e.getMessage());
                return "upi/pay";
            }
        });
    }

    /** Step 2: the customer entered the UPI PIN. */
    @PostMapping("/pay/confirm")
    public String pay(@RequestParam String payeeVpa, @RequestParam String amount,
                      @RequestParam(required = false) String note, @RequestParam(required = false) String pin,
                      Principal principal, Model model, RedirectAttributes redirect) {
        return withVpa(principal, vpa -> {
            try {
                var t = upi.pay(vpa, payeeVpa, parseAmount(amount), note, pin);
                redirect.addFlashAttribute("payment", t);
                redirect.addFlashAttribute("payee", upi.lookup(vpa, payeeVpa));
                return "redirect:/upi/success";
            } catch (BankException e) {
                model.addAttribute("error", e.getMessage());
                model.addAttribute("payeeVpa", payeeVpa);
                model.addAttribute("amount", amount);
                model.addAttribute("note", note);
                try {
                    model.addAttribute("payee", upi.lookup(vpa, payeeVpa));
                    model.addAttribute("amountValue", parseAmount(amount));
                    return "upi/confirm";
                } catch (BankException again) {
                    return "upi/pay";
                }
            }
        });
    }

    @GetMapping("/success")
    public String success(Model model) {
        return model.containsAttribute("payment") ? "upi/success" : "redirect:/upi";
    }

    @GetMapping("/receive")
    public String receive(Principal principal, Model model) {
        return withVpa(principal, vpa -> {
            model.addAttribute("profile", upi.profile(vpa));
            model.addAttribute("qr", qrCodes.svg(upi.paymentUri(vpa)));
            return "upi/receive";
        });
    }

    @GetMapping("/balance")
    public String balanceForm(Principal principal) {
        return withVpa(principal, vpa -> "upi/balance");
    }

    @PostMapping("/balance")
    public String balance(@RequestParam(required = false) String pin, Principal principal, Model model) {
        return withVpa(principal, vpa -> {
            try {
                model.addAttribute("balance", upi.balance(vpa, pin));
                model.addAttribute("profile", upi.profile(vpa));
            } catch (BankException e) {
                model.addAttribute("error", e.getMessage());
            }
            return "upi/balance";
        });
    }

    @GetMapping("/history")
    public String history(Principal principal, Model model) {
        return withVpa(principal, vpa -> {
            model.addAttribute("transactions", upi.history(vpa));
            return "upi/history";
        });
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("Please enter a valid amount");
        }
    }
}
