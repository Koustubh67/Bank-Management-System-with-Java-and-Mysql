package com.koustubh.bank.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** At startup: price any older mutual fund holdings that don't have units yet, then pay any SIP instalments due. */
@Component
@Order(3)
public class InvestmentMaintenance implements ApplicationRunner {

    private final InvestmentService investments;

    public InvestmentMaintenance(InvestmentService investments) {
        this.investments = investments;
    }

    @Override
    public void run(ApplicationArguments args) {
        investments.backfillMissingUnits();
        investments.processDueSips();
    }
}
