package com.koustubh.bank.web;

import com.koustubh.bank.domain.FdTenure;
import com.koustubh.bank.domain.Fund;
import com.koustubh.bank.domain.InsurancePlan;
import com.koustubh.bank.domain.InsurancePolicy;
import com.koustubh.bank.domain.Investment;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.WealthService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.format.DateTimeFormatter;

/** Invest (FD, SIP) and Insurance pages inside net banking. */
@Controller
@RequestMapping("/customer")
public class WealthController {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final WealthService wealth;
    private final CustomerService customers;
    private final Money money;

    public WealthController(WealthService wealth, CustomerService customers, Money money) {
        this.wealth = wealth;
        this.customers = customers;
        this.money = money;
    }

    @GetMapping("/invest")
    public String invest(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("portfolio", wealth.portfolio(principal.getName()));
        model.addAttribute("tenures", FdTenure.values());
        model.addAttribute("funds", Fund.values());
        return "customer/invest";
    }

    @PostMapping("/invest/fd")
    public String openFd(@RequestParam(required = false) String amount, @RequestParam(required = false) FdTenure tenure,
                         Principal principal, RedirectAttributes redirect) {
        try {
            Investment fd = wealth.openFixedDeposit(principal.getName(), parse(amount), tenure);
            redirect.addFlashAttribute("message", "Fixed deposit " + fd.getReference() + " opened. It matures on "
                    + fd.getMaturityDate().format(DATE) + " at " + money.format(fd.getMaturityAmount()) + ".");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/invest";
    }

    @PostMapping("/invest/sip")
    public String startSip(@RequestParam(required = false) String amount, @RequestParam(required = false) Fund fund,
                           Principal principal, RedirectAttributes redirect) {
        try {
            Investment sip = wealth.startSip(principal.getName(), fund, parse(amount));
            redirect.addFlashAttribute("message", "SIP " + sip.getReference() + " started in " + fund.getLabel()
                    + ". First instalment of " + money.format(sip.getAmount()) + " debited.");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/invest";
    }

    @GetMapping("/insurance")
    public String insurance(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("portfolio", wealth.portfolio(principal.getName()));
        model.addAttribute("plans", InsurancePlan.values());
        model.addAttribute("quotes", wealth.quotes(principal.getName()));
        return "customer/insurance";
    }

    @PostMapping("/insurance")
    public String buy(@RequestParam(required = false) InsurancePlan plan, @RequestParam(required = false) Long cover,
                      @RequestParam(required = false) String details, Principal principal, RedirectAttributes redirect) {
        try {
            InsurancePolicy policy = wealth.buyPolicy(principal.getName(), plan, cover, details);
            redirect.addFlashAttribute("message", "You're covered! " + plan.getLabel() + " policy "
                    + policy.getPolicyNumber() + " is active until " + policy.getEndDate().format(DATE) + ".");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/insurance";
    }

    private static BigDecimal parse(String amount) {
        try {
            return new BigDecimal(amount.trim().replace(",", ""));
        } catch (NullPointerException | NumberFormatException e) {
            throw new InvalidRequestException("Please enter a valid amount");
        }
    }
}
