package com.koustubh.bank.web;

import com.koustubh.bank.dto.PinChangeForm;
import com.koustubh.bank.dto.TransferForm;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.AtmService;
import com.koustubh.bank.service.CardSecurityService;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.TransferService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;

/** ATM screens, opened from the customer dashboard. The card number is kept in the session after the PIN check. */
@Controller
@RequestMapping("/atm")
public class AtmController {

    /** Session attribute holding the card number once the PIN has been entered at the ATM. */
    public static final String ATM_CARD = "ATM_CARD";

    static final List<Integer> FAST_CASH_AMOUNTS = List.of(500, 1000, 2000, 5000, 10000, 20000);

    private final AtmService atm;
    private final TransferService transfers;
    private final CardSecurityService cardSecurity;
    private final CustomerService customers;

    public AtmController(AtmService atm, TransferService transfers, CardSecurityService cardSecurity,
                         CustomerService customers) {
        this.customers = customers;
        this.atm = atm;
        this.transfers = transfers;
        this.cardSecurity = cardSecurity;
    }

    /** "Insert card" screen: the logged-in customer's own card is used, so only the PIN is asked for. */
    @GetMapping("/login")
    public String login(Principal principal, Model model) {
        model.addAttribute("maskedCard", mask(customers.cardNumber(principal.getName())));
        return "atm/login";
    }

    @PostMapping("/insert")
    public String insertCard(@RequestParam(required = false) String pin, Principal principal, HttpSession session,
                             Model model) {
        String cardNumber = customers.cardNumber(principal.getName());
        try {
            cardSecurity.verifyLogin(cardNumber, pin == null ? "" : pin);
        } catch (AuthenticationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("maskedCard", mask(cardNumber));
            return "atm/login";
        }
        session.setAttribute(ATM_CARD, cardNumber);
        return "redirect:/atm";
    }

    /** Ends the ATM session (card ejected) and goes back to the customer dashboard. */
    @PostMapping("/exit")
    public String exit(HttpSession session, RedirectAttributes redirect) {
        session.removeAttribute(ATM_CARD);
        redirect.addFlashAttribute("message", "ATM session ended. Please take your card.");
        return "redirect:/customer";
    }

    @GetMapping
    public String menu(@SessionAttribute(ATM_CARD) String card, Model model) {
        model.addAttribute("summary", atm.summary(card));
        return "atm/menu";
    }

    @GetMapping("/deposit")
    public String depositForm() {
        return "atm/deposit";
    }

    @PostMapping("/deposit")
    public String deposit(@RequestParam(required = false) String amount, @SessionAttribute(ATM_CARD) String card, Model model,
                          RedirectAttributes redirect) {
        return handle("atm/deposit", model, redirect,
                () -> Receipt.of(atm.deposit(card, parseAmount(amount))));
    }

    @GetMapping("/withdraw")
    public String withdrawForm() {
        return "atm/withdraw";
    }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam(required = false) String amount, @SessionAttribute(ATM_CARD) String card, Model model,
                           RedirectAttributes redirect) {
        return handle("atm/withdraw", model, redirect,
                () -> Receipt.of(atm.withdraw(card, parseAmount(amount))));
    }

    @GetMapping("/fast-cash")
    public String fastCashForm(Model model) {
        model.addAttribute("amounts", FAST_CASH_AMOUNTS);
        return "atm/fast-cash";
    }

    @PostMapping("/fast-cash")
    public String fastCash(@RequestParam(required = false) String amount, @SessionAttribute(ATM_CARD) String card, Model model,
                           RedirectAttributes redirect) {
        model.addAttribute("amounts", FAST_CASH_AMOUNTS);
        return handle("atm/fast-cash", model, redirect,
                () -> Receipt.of(atm.withdraw(card, parseAmount(amount))));
    }

    @GetMapping("/transfer")
    public String transferForm(Model model) {
        model.addAttribute("transferForm", new TransferForm());
        return "atm/transfer";
    }

    @PostMapping("/transfer")
    public String transfer(@Valid @ModelAttribute TransferForm transferForm, BindingResult result,
                           @SessionAttribute(ATM_CARD) String card, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "atm/transfer";
        }
        return handle("atm/transfer", model, redirect, () -> Receipt.of(transfers.transfer(card,
                transferForm.getToAccountNumber(), transferForm.getAmount())));
    }

    @GetMapping("/balance")
    public String balance(@SessionAttribute(ATM_CARD) String card, Model model) {
        model.addAttribute("summary", atm.summary(card));
        return "atm/balance";
    }

    @GetMapping("/statement")
    public String statement(@SessionAttribute(ATM_CARD) String card, Model model) {
        model.addAttribute("summary", atm.summary(card));
        model.addAttribute("transactions", atm.miniStatement(card));
        return "atm/statement";
    }

    @GetMapping("/pin")
    public String pinForm(Model model) {
        model.addAttribute("pinChangeForm", new PinChangeForm());
        return "atm/pin";
    }

    @PostMapping("/pin")
    public String changePin(@Valid @ModelAttribute PinChangeForm pinChangeForm, BindingResult result,
                            @SessionAttribute(ATM_CARD) String card, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "atm/pin";
        }
        try {
            cardSecurity.changePin(card, pinChangeForm.getCurrentPin(), pinChangeForm.getNewPin());
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

    private static String mask(String cardNumber) {
        return "XXXX XXXX XXXX " + cardNumber.substring(12);
    }

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("Please enter a valid amount");
        }
    }
}
