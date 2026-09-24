package com.koustubh.bank.web;

import com.koustubh.bank.domain.FdTenure;
import com.koustubh.bank.domain.LoanType;
import com.koustubh.bank.service.LendingRateService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Public money tools (no login): EMI with fixed vs floating, loan eligibility, SIP, lump sum and FD calculators. */
@Controller
public class ToolsController {

    private final LendingRateService rates;

    public ToolsController(LendingRateService rates) {
        this.rates = rates;
    }

    @GetMapping("/tools")
    public String tools(Model model) {
        model.addAttribute("offers", rates.offers());
        model.addAttribute("repo", rates.current());
        model.addAttribute("selected", LoanType.HOME);
        model.addAttribute("tenures", FdTenure.values());
        return "tools/index";
    }
}
