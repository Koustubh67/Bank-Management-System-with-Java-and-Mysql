package com.koustubh.bank.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** At startup: price older mutual fund holdings without units, then pay any SIP instalments and loan EMIs due. */
@Component
@Order(3)
public class InvestmentMaintenance implements ApplicationRunner {

    private final InvestmentService investments;
    private final LoanService loans;

    public InvestmentMaintenance(InvestmentService investments, LoanService loans) {
        this.investments = investments;
        this.loans = loans;
    }

    @Override
    public void run(ApplicationArguments args) {
        investments.backfillMissingUnits();
        investments.processDueSips();
        loans.collectDueEmis();
    }
}
