package com.koustubh.bank.web;

import com.koustubh.bank.domain.LoanEnquiry;
import com.koustubh.bank.domain.LoanType;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.LendingRateService;
import com.koustubh.bank.service.LoanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

/** Public Loans page: EMI calculators for every loan type and an enquiry form for people who aren't customers yet. */
@Controller
@RequestMapping("/loans")
public class LoanPublicController {

    private final LoanService loans;
    private final LendingRateService rates;

    public LoanPublicController(LoanService loans, LendingRateService rates) {
        this.loans = loans;
        this.rates = rates;
    }

    @GetMapping
    public String loans(@RequestParam(required = false) LoanType type, Model model) {
        model.addAttribute("types", LoanType.values());
        model.addAttribute("offers", rates.offers());
        model.addAttribute("repo", rates.current());
        model.addAttribute("selected", type == null ? LoanType.HOME : type);
        model.addAttribute("employment", LoanService.EMPLOYMENT);
        model.addAttribute("times", LoanService.CALL_TIMES);
        return "loans/index";
    }

    @PostMapping("/enquiry")
    public String enquire(@RequestParam(required = false) String name, @RequestParam(required = false) String mobile,
                          @RequestParam(required = false) String email, @RequestParam(required = false) String city,
                          @RequestParam(required = false) LoanType type, @RequestParam(required = false) BigDecimal amount,
                          @RequestParam(required = false) String employment,
                          @RequestParam(required = false) BigDecimal monthlyIncome,
                          @RequestParam(required = false) String preferredTime, RedirectAttributes redirect) {
        LoanService.Enquiry form = new LoanService.Enquiry(name, mobile, email, city, type, amount, employment,
                monthlyIncome, preferredTime);
        try {
            LoanEnquiry saved = loans.enquire(form);
            redirect.addFlashAttribute("enquiry", saved);
            return "redirect:/loans/thanks";
        } catch (BankException e) {
            redirect.addFlashAttribute("enquiryForm", form);
            redirect.addFlashAttribute("enquiryErrors", List.of(e.getMessage().split(" · ")));
            return "redirect:/loans" + (type == null ? "" : "?type=" + type) + "#enquire";
        }
    }

    @GetMapping("/thanks")
    public String thanks(Model model) {
        return model.containsAttribute("enquiry") ? "loans/thanks" : "redirect:/loans";
    }
}
