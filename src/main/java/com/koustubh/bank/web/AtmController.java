package com.koustubh.bank.web;

import com.koustubh.bank.dto.PinChangeForm;
import com.koustubh.bank.dto.TransferForm;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.AtmService;
import com.koustubh.bank.service.CardSecurityService;
import com.koustubh.bank.service.TransferService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;

/** ATM screens. The logged-in principal's name is the card number. */
@Controller
@RequestMapping("/atm")
public class AtmController {

    static final List<Integer> FAST_CASH_AMOUNTS = List.of(500, 1000, 2000, 5000, 10000, 20000);

    private final AtmService atm;
    private final TransferService transfers;
    private final CardSecurityService cardSecurity;

    public AtmController(AtmService atm, TransferService transfers, CardSecurityService cardSecurity) {
        this.atm = atm;
        this.transfers = transfers;
        this.cardSecurity = cardSecurity;
    }

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, HttpSession session, Model model) {
        if (error != null) {
            Object ex = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            model.addAttribute("error", ex instanceof AuthenticationException ae ? ae.getMessage() : "Login failed");
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
        return "atm/login";
    }

    @GetMapping
    public String menu(Principal principal, Model model) {
        model.addAttribute("summary", atm.summary(principal.getName()));
        return "atm/menu";
    }

    @GetMapping("/deposit")
    public String depositForm() {
        return "atm/deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam(required = false) String amount, Principal principal, Model model,
                          RedirectAttributes redirect) {
        return handle("atm/deposit", model, redirect,
                () -> Receipt.of(atm.deposit(principal.getName(), parseAmount(amount))));
    }

    @GetMapping("/withdraw")
    public String withdrawForm() {
        return "atm/withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam(required = false) String amount, Principal principal, Model model,
                           RedirectAttributes redirect) {
        return handle("atm/withdraw", model, redirect,
                () -> Receipt.of(atm.withdraw(principal.getName(), parseAmount(amount))));
    }

    @GetMapping("/fast-cash")
    public String fastCashForm(Model model) {
        model.addAttribute("amounts", FAST_CASH_AMOUNTS);
        return "atm/fast-cash";
    }

    @PostMapping("/fast-cash")
    public String fastCash(@RequestParam(required = false) String amount, Principal principal, Model model,
                           RedirectAttributes redirect) {
        model.addAttribute("amounts", FAST_CASH_AMOUNTS);
        return handle("atm/fast-cash", model, redirect,
                () -> Receipt.of(atm.withdraw(principal.getName(), parseAmount(amount))));
    }

    @GetMapping("/transfer")
    public String transferForm(Model model) {
        model.addAttribute("transferForm", new TransferForm());
        return "atm/transfer";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute TransferForm transferForm, BindingResult result,
                           Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "atm/transfer";
        }
        return handle("atm/transfer", model, redirect, () -> Receipt.of(transfers.transfer(principal.getName(),
                transferForm.getToAccountNumber(), transferForm.getAmount())));
    }

    @GetMapping("/balance")
    public String balance(Principal principal, Model model) {
        model.addAttribute("summary", atm.summary(principal.getName()));
        return "atm/balance";
    }

    @GetMapping("/statement")
    public String statement(Principal principal, Model model) {
        model.addAttribute("summary", atm.summary(principal.getName()));
        model.addAttribute("transactions", atm.miniStatement(principal.getName()));
        return "atm/statement";
    }

    @GetMapping("/pin")
    public String pinForm(Model model) {
        model.addAttribute("pinChangeForm", new PinChangeForm());
        return "atm/pin";
    }

    @PostMapping("/pin")
    public String changePin(@Valid @ModelAttribute PinChangeForm pinChangeForm, BindingResult result,
                            Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "atm/pin";
        }
        try {
            cardSecurity.changePin(principal.getName(), pinChangeForm.getCurrentPin(), pinChangeForm.getNewPin());
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
            return "atm/pin";
        }
        redirect.addFlashAttribute("message", "Your PIN has been changed");
        return "redirect:/atm";
    }

    @GetMapping("/receipt")
    public String receipt(Model model) {
        return model.containsAttribute("receipt") ? "atm/receipt" : "redirect:/atm";
    }

    /** Runs a money operation: on success shows the receipt, on a business error re-shows the form with the message. */
    private String handle(String view, Model model, RedirectAttributes redirect, Supplier<Receipt> operation) {
        try {
            redirect.addFlashAttribute("receipt", operation.get());
            return "redirect:/atm/receipt";
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
            return view;
        }
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("Please enter a valid amount");
        }
    }
}
