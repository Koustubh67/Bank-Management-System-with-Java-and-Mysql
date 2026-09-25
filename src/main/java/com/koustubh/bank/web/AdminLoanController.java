package com.koustubh.bank.web;

import com.koustubh.bank.domain.LoanEnquiry;
import com.koustubh.bank.domain.LoanStatus;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.EmiCalculator;
import com.koustubh.bank.service.EmiPaymentService;
import com.koustubh.bank.service.LendingRateService;
import com.koustubh.bank.service.LoanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Loan officers: decide applications, follow active loans and EMIs, call back public enquiries. Branch managers also
 * record repo rate changes, which reprice every floating-rate loan.
 */
@Controller
@RequestMapping("/admin/loans")
public class AdminLoanController {

    private final LoanService loans;
    private final LendingRateService rates;
    private final EmiPaymentService payments;
    private final Clock clock;

    public AdminLoanController(LoanService loans, LendingRateService rates, EmiPaymentService payments, Clock clock) {
        this.loans = loans;
        this.rates = rates;
        this.payments = payments;
        this.clock = clock;
    }

    @GetMapping
    public String home(Model model) {
        model.addAttribute("applications", loans.byStatus(LoanStatus.APPLIED));
        model.addAttribute("active", loans.byStatus(LoanStatus.ACTIVE));
        model.addAttribute("decided", loans.byStatus(LoanStatus.REJECTED, LoanStatus.CLOSED));
        model.addAttribute("enquiries", loans.recentEnquiries());
        model.addAttribute("repo", rates.current());
        model.addAttribute("repoHistory", rates.history());
        model.addAttribute("floatingLoans", rates.activeFloatingLoans());
        return "admin/loans";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("v", loans.loanForStaff(id));
        // If approved today, the first EMI would fall on this date (same rule as LoanService.approve)
        model.addAttribute("firstEmi", EmiCalculator.firstEmiDate(LocalDate.now(clock)));
        model.addAttribute("repo", rates.current());
        return "admin/loan";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) BigDecimal principal,
                          @RequestParam(required = false) BigDecimal rate, @RequestParam(required = false) Integer months,
                          Principal staff, RedirectAttributes redirect) {
        try {
            var loan = loans.approve(id, staff.getName(), principal, rate, months);
            redirect.addFlashAttribute("message", "Loan " + loan.getReference() + " approved and Rs "
                    + loan.getPrincipal().toPlainString() + " disbursed. EMI Rs " + loan.getEmi().toPlainString()
                    + " from " + loan.getFirstEmiDate() + " to " + loan.getEndDate() + ". Customer notified");
            return "redirect:/admin/loans/" + id;
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/loans/" + id;
        }
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String reason, Principal staff,
                         RedirectAttributes redirect) {
        try {
            loans.reject(id, staff.getName(), reason);
            redirect.addFlashAttribute("message", "Application rejected. Customer notified");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/loans/" + id;
    }

    @GetMapping("/payments/{ref}/receipt")
    public String receipt(@PathVariable String ref, Model model) {
        EmiPaymentService.Receipt r = payments.receiptForStaff(ref);
        model.addAttribute("r", r);
        model.addAttribute("backUrl", "/admin/loans/" + r.loan().getId());
        return "loans/receipt";
    }

    /** Branch managers only (see SecurityConfig): record a new repo rate and reprice floating-rate loans. */
    @PostMapping("/repo-rate")
    public String repoRate(@RequestParam(required = false) BigDecimal rate, @RequestParam(required = false) String note,
                           Principal staff, RedirectAttributes redirect) {
        try {
            var result = rates.changeRepoRate(rate, note, staff.getName());
            redirect.addFlashAttribute("message", "Repo rate is now " + result.change().getNewRate() + "% (was "
                    + result.change().getOldRate() + "%). " + result.repriced()
                    + " floating-rate loan(s) repriced and the customers notified");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/loans#repo";
    }

    @PostMapping("/collect")
    public String collect(RedirectAttributes redirect) {
        int paid = loans.collectDueEmis();
        redirect.addFlashAttribute("message", paid == 0 ? "No EMIs could be collected (none due, or balances too low)"
                : paid + " EMI(s) collected");
        return "redirect:/admin/loans";
    }

    @PostMapping("/enquiries/{id}")
    public String enquiry(@PathVariable Long id, @RequestParam(required = false) LoanEnquiry.Status status,
                          @RequestParam(required = false) String note, Principal staff, RedirectAttributes redirect) {
        try {
            loans.updateEnquiry(id, status, staff.getName(), note);
            redirect.addFlashAttribute("message", "Enquiry updated");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/loans#enquiries";
    }
}
