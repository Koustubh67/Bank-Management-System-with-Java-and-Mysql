package com.koustubh.bank.market;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Predictable NAVs for tests, so they never depend on the internet: every fund's NAV rises by 0.01 a day and is
 * exactly 100.00 today (so on day d in the past it is 100 - 0.01 × d).
 */
@Component
@Profile("test")
public class FakeNavSource implements NavSource {

    public static final BigDecimal TODAY_NAV = new BigDecimal("100.00");

    private final Clock clock;

    public FakeNavSource(Clock clock) {
        this.clock = clock;
    }

    @Override
    public FundHistory fetch(long schemeCode) {
        LocalDate today = LocalDate.now(clock);
        List<NavPoint> navs = new ArrayList<>();
        for (int d = 0; d < 6 * 365; d++) {
            navs.add(new NavPoint(today.minusDays(d), TODAY_NAV.subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(d)))));
        }
        String name = FundCatalog.find(schemeCode).map(FundCatalog.Listing::shortName).orElse("Fund") + " - Direct Plan - Growth";
        return new FundHistory(schemeCode, name, "Test Mutual Fund", "Equity Scheme - Test", navs);
    }
}
