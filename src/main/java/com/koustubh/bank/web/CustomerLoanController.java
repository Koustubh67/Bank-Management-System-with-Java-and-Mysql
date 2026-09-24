package com.koustubh.bank.web;

import com.koustubh.bank.domain.Loan;
import com.koustubh.bank.domain.LoanInstalment;
import com.koustubh.bank.domain.LoanType;
import com.koustubh.bank.domain.RateType;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.LendingRateService;
import com.koustubh.bank.service.LoanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Loans inside net banking: apply, track the decision, see the EMI schedule and pay EMIs. */
@Controller
@RequestMapping("/customer/loans")
public class CustomerLoanController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final LoanService loans;
    private final CustomerService customers;
    private final LendingRateService rates;

    public CustomerLoanController(LoanService loans, CustomerService customers, LendingRateService rates) {
        this.loans = loans;
        this.customers = customers;
        this.rates = rates;
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("loans", loans.loansOf(principal.getName()));
        model.addAttribute("offers", rates.offers());
        return "customer/loans";
    }

    @GetMapping("/apply")
    public String applyForm(@RequestParam(required = false) LoanType type, Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("offers", rates.offers());
        model.addAttribute("repo", rates.current());
        model.addAttribute("rateTypes", RateType.values());
        model.addAttribute("employment", LoanService.EMPLOYMENT);
        if (!model.containsAttribute("form")) {
            LoanType t = type == null ? LoanType.PERSONAL : type;
            model.addAttribute("form", new LoanService.Application(t, RateType.FLOATING, t.getMinAmount().multiply(BigDecimal.valueOf(4)),
                    Math.min(t.getMaxMonths(), Math.max(t.getMinMonths(), 36)), "", "Salaried", null));
        }
        return "customer/loan-apply";
    }

    @PostMapping("/apply")
    public String apply(@RequestParam(required = false) LoanType type, @RequestParam(required = false) RateType rateType,
                        @RequestParam(required = false) BigDecimal amount,
                        @RequestParam(required = false) Integer months, @RequestParam(required = false) String purpose,
                        @RequestParam(required = false) String employment,
                        @RequestParam(required = false) BigDecimal monthlyIncome,
                        @RequestParam(required = false) boolean declaration, Principal principal, RedirectAttributes redirect) {
        LoanService.Application form = new LoanService.Application(type, rateType, amount, months, purpose, employment,
                monthlyIncome);
        try {
            if (!declaration) {
                throw new BankException("Please confirm the declaration");
            }
            Loan loan = loans.apply(principal.getName(), form);
            redirect.addFlashAttribute("applied", loan.getReference());
            redirect.addFlashAttribute("appliedType", loan.getType().getLabel());
            return "redirect:/customer/loans/applied";
        } catch (BankException e) {
            redirect.addFlashAttribute("form", form);
            redirect.addFlashAttribute("errors", List.of(e.getMessage().split(" · ")));
            return "redirect:/customer/loans/apply";
        }
    }

    @GetMapping("/applied")
    public String applied(Principal principal, Model model) {
        if (!model.containsAttribute("applied")) {
            return "redirect:/customer/loans";
        }
        model.addAttribute("o", customers.overview(principal.getName()));
        return "customer/loan-applied";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("v", loans.loanOf(principal.getName(), id));
        return "customer/loan";
    }

    @PostMapping("/{id}/pay")
    public String payNext(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        try {
            LoanInstalment paid = loans.payNext(principal.getName(), id);
            redirect.addFlashAttribute("message", "EMI " + paid.getNumber() + " (due " + paid.getDueDate().format(DATE)
                    + ") paid. Principal still owed: Rs " + paid.getBalanceAfter().toPlainString());
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/loans/" + id;
    }
}
