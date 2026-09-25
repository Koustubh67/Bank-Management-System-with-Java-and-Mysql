package com.koustubh.bank.web;

import com.koustubh.bank.domain.EmiPayment;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.EmiPaymentService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Paying an EMI: choose savings account, card or UPI; card payments continue on an OTP page and UPI payments on a
 * QR code page, both with 5 minutes to pay; then the success page and a printable receipt.
 */
@Controller
@RequestMapping("/customer/loans")
public class LoanPaymentController {

    private final EmiPaymentService payments;
    private final CustomerService customers;
    private final QrCodes qrCodes;
    private final Clock clock;

    public LoanPaymentController(EmiPaymentService payments, CustomerService customers, QrCodes qrCodes, Clock clock) {
        this.payments = payments;
        this.customers = customers;
        this.qrCodes = qrCodes;
        this.clock = clock;
    }

    @GetMapping("/{id}/pay")
    public String choose(@PathVariable Long id, Principal principal, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("due", payments.due(principal.getName(), id));
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/customer/loans/" + id;
        }
        model.addAttribute("o", customers.overview(principal.getName()));
        return "customer/loan-pay";
    }

    @PostMapping("/{id}/pay/account")
    public String payFromAccount(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            return toPayment(payments.payFromAccount(principal.getName(), id));
        } catch (BankException e) {
            redirect.addFlashAttribute("payError", e.getMessage());
            return "redirect:/customer/loans/" + id + "/pay#account";
        }
    }

    @PostMapping("/{id}/pay/card")
    public String payByCard(@PathVariable Long id, @RequestParam(required = false) String cardNumber,
                            @RequestParam(required = false) String cardHolder, @RequestParam(required = false) String expiry,
                            @RequestParam(required = false) String cvv, Principal principal, RedirectAttributes redirect) {
        try {
            EmiPaymentService.CardStarted started = payments.startCard(principal.getName(), id,
                    new EmiPaymentService.Card(cardNumber, cardHolder, expiry, cvv));
            redirect.addFlashAttribute("smsOtp", started.otp());
            return toPayment(started.payment());
        } catch (BankException e) {
            // Everything typed is kept except the CVV, which is never sent back to the browser
            redirect.addFlashAttribute("cardErrors", List.of(e.getMessage().split(" · ")));
            redirect.addFlashAttribute("cardNumber", cardNumber);
            redirect.addFlashAttribute("cardHolder", cardHolder);
            redirect.addFlashAttribute("expiry", expiry);
            return "redirect:/customer/loans/" + id + "/pay#card";
        }
    }

    @PostMapping("/{id}/pay/upi")
    public String payByUpi(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            return toPayment(payments.startUpi(principal.getName(), id));
        } catch (BankException e) {
            redirect.addFlashAttribute("payError", e.getMessage());
            return "redirect:/customer/loans/" + id + "/pay#upi";
        }
    }

    /** One page per payment: OTP or QR code while pending, then success, or why it didn't go through. */
    @GetMapping("/payments/{ref}")
    public String payment(@PathVariable String ref, Principal principal, Model model) {
        EmiPayment p = payments.current(principal.getName(), ref);
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("p", p);
        if (p.getStatus() == EmiPayment.Status.PAID) {
            model.addAttribute("r", payments.receipt(principal.getName(), ref));
            return "customer/loan-paid";
        }
        if (!p.isPending()) {
            return "customer/loan-pay-failed";
        }
        model.addAttribute("secondsLeft", secondsLeft(p));
        if (p.getMethod() == EmiPayment.Method.UPI) {
            model.addAttribute("qr", qrCodes.svg(payments.upiLink(p)));
            model.addAttribute("vpa", EmiPaymentService.COLLECTION_VPA);
            return "customer/loan-pay-upi";
        }
        return "customer/loan-pay-otp";
    }

    @PostMapping("/payments/{ref}/otp")
    public String confirmOtp(@PathVariable String ref, @RequestParam(required = false) String otp, Principal principal,
                             RedirectAttributes redirect) {
        try {
            payments.confirmCard(principal.getName(), ref, otp);
        } catch (BankException e) {
            redirect.addFlashAttribute("payError", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("payError", "This payment changed while you were paying. Here is its latest status");
        }
        return "redirect:/customer/loans/payments/" + ref;
    }

    /** Demo stand-in for the UPI network telling the bank the customer approved the payment in their UPI app. */
    @PostMapping("/payments/{ref}/upi-paid")
    public String upiPaid(@PathVariable String ref, Principal principal, RedirectAttributes redirect) {
        try {
            payments.upiPaid(principal.getName(), ref);
        } catch (BankException e) {
            redirect.addFlashAttribute("payError", e.getMessage());
        } catch (OptimisticLockingFailureException e) {
            redirect.addFlashAttribute("payError", "This payment changed while you were paying. Here is its latest status");
        }
        return "redirect:/customer/loans/payments/" + ref;
    }

    @PostMapping("/payments/{ref}/cancel")
    public String cancel(@PathVariable String ref, Principal principal, RedirectAttributes redirect) {
        EmiPayment p = payments.cancel(principal.getName(), ref);
        redirect.addFlashAttribute("message", p.getStatus() == EmiPayment.Status.PAID
                ? "This EMI was already paid (receipt " + p.getReference() + ")" : "Payment cancelled. Nothing was charged");
        return "redirect:/customer/loans/" + p.getLoan().getId();
    }

    /** Polled by the QR code page every few seconds, like a payment app waiting for the bank. */
    @GetMapping(value = "/payments/{ref}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> status(@PathVariable String ref, Principal principal) {
        EmiPayment p = payments.current(principal.getName(), ref);
        return Map.of("status", p.getStatus().name(), "secondsLeft", p.isPending() ? secondsLeft(p) : 0);
    }

    @GetMapping("/payments/{ref}/receipt")
    public String receipt(@PathVariable String ref, Principal principal, Model model) {
        EmiPaymentService.Receipt r = payments.receipt(principal.getName(), ref);
        model.addAttribute("r", r);
        model.addAttribute("backUrl", "/customer/loans/" + r.loan().getId());
        return "loans/receipt";
    }

    private long secondsLeft(EmiPayment p) {
        return Math.max(0, Duration.between(LocalDateTime.now(clock), p.getExpiresAt()).toSeconds());
    }

    private static String toPayment(EmiPayment p) {
        return "redirect:/customer/loans/payments/" + p.getReference();
    }
}
