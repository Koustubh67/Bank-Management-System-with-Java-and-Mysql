package com.koustubh.bank.market;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Loads fund prices in the background at startup, so the first visitor to the Invest page doesn't wait. */
@Component
@Profile("!test")
public class MarketWarmup implements ApplicationRunner {

    private final MarketDataService market;

    public MarketWarmup(MarketDataService market) {
        this.market = market;
    }

    @Override
    public void run(ApplicationArguments args) {
        Thread warm = new Thread(market::catalogue, "nav-warmup");
        warm.setDaemon(true);
        warm.start();
    }
}
