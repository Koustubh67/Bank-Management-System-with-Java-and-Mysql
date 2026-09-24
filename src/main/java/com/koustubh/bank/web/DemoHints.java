package com.koustubh.bank.web;

import com.koustubh.bank.config.BankProperties;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Lets login pages show the demo credentials, but only while the demo customers are loaded. */
@ControllerAdvice
public class DemoHints {

    private final boolean demoEnabled;

    public DemoHints(BankProperties properties) {
        this.demoEnabled = properties.demo() != null && properties.demo().enabled();
    }

    @ModelAttribute("demoEnabled")
    public boolean demoEnabled() {
        return demoEnabled;
    }
}
