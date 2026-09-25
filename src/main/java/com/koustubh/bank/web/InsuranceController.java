package com.koustubh.bank.web;

import com.koustubh.bank.domain.Customer;
import com.koustubh.bank.domain.InsurancePlan;
import com.koustubh.bank.domain.InsuranceRequest;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.InsuranceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/** Insurance in net banking: browse plans, ask an expert to call back, track requests, open issued policies. */
@Controller
@RequestMapping("/customer/insurance")
public class InsuranceController {

    private final InsuranceService insurance;
    private final CustomerService customers;
    private final Clock clock;

    public InsuranceController(InsuranceService insurance, CustomerService customers, Clock clock) {
        this.insurance = insurance;
        this.customers = customers;
        this.clock = clock;
    }

    @GetMapping
    public String home(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("plans", InsurancePlan.values());
        model.addAttribute("requests", insurance.requestsOf(principal.getName()));
        model.addAttribute("policies", insurance.policiesOf(principal.getName()));
        model.addAttribute("today", LocalDate.now(clock));
        return "customer/insurance";
    }

    @GetMapping("/apply/{plan}")
    public String applyForm(@PathVariable InsurancePlan plan, Principal principal, Model model) {
        CustomerService.Overview o = customers.overview(principal.getName());
        Customer c = o.customer();
        model.addAttribute("o", o);
        model.addAttribute("plan", plan);
        model.addAttribute("times", InsuranceService.CALL_TIMES);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new InsuranceService.Request(plan, plan.getCovers().get(1 % plan.getCovers().size()),
                    c.getFullName(), c.getMobile(), c.getCity(), Period.between(c.getDateOfBirth(), LocalDate.now(clock)).getYears(),
                    "", InsuranceService.CALL_TIMES.get(0)));
        }
        return "customer/insurance-apply";
    }

    @PostMapping("/apply/{plan}")
    public String apply(@PathVariable InsurancePlan plan, @RequestParam(required = false) Long cover,
                        @RequestParam(required = false) String contactName, @RequestParam(required = false) String mobile,
                        @RequestParam(required = false) String city, @RequestParam(required = false) Integer age,
                        @RequestParam(required = false) String extra, @RequestParam(required = false) String preferredTime,
                        Principal principal, RedirectAttributes redirect) {
        InsuranceService.Request form = new InsuranceService.Request(plan, cover, contactName, mobile, city, age, extra, preferredTime);
        try {
            InsuranceRequest saved = insurance.request(principal.getName(), form);
            redirect.addFlashAttribute("requested", saved.getReference());
            redirect.addFlashAttribute("requestedPlan", plan.getLabel());
            redirect.addFlashAttribute("requestedMobile", saved.getMobile());
            redirect.addFlashAttribute("requestedTime", saved.getPreferredTime());
            return "redirect:/customer/insurance/requested";
        } catch (BankException e) {
            // Keep everything the customer typed and show every problem at once
            redirect.addFlashAttribute("form", form);
            redirect.addFlashAttribute("errors", List.of(e.getMessage().split(" · ")));
            return "redirect:/customer/insurance/apply/" + plan;
        }
    }

    @GetMapping("/requested")
    public String requested(Principal principal, Model model) {
        if (!model.containsAttribute("requested")) {
            return "redirect:/customer/insurance";
        }
        model.addAttribute("o", customers.overview(principal.getName()));
        return "customer/insurance-requested";
    }

    @GetMapping("/policies/{id}")
    public String policy(@PathVariable Long id, Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("p", insurance.policyOf(principal.getName(), id));
        model.addAttribute("today", LocalDate.now(clock));
        return "customer/policy";
    }
}
