package com.koustubh.bank.web;

import com.koustubh.bank.TestAccounts;
import com.koustubh.bank.market.MarketDataService;
import com.koustubh.bank.service.OpenedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The public Invest marketplace (test NAVs: 100.00 today, 0.01 lower for each day back). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketWebTest {

    @Autowired MockMvc mvc;
    @Autowired TestAccounts accounts;
    @Autowired MarketDataService market;

    @Test
    void anyoneCanBrowseFundsAndChartsWithoutLoggingIn() throws Exception {
        mvc.perform(get("/invest")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Parag Parikh Flexi Cap")))
                .andExpect(content().string(containsString("Nippon India Gold Savings")))
                .andExpect(content().string(containsString("Top gainers")))
                .andExpect(content().string(containsString("Market today")));
        mvc.perform(get("/invest/funds/122639")).andExpect(status().isOk())
                .andExpect(content().string(containsString("What if you had invested?")))
                .andExpect(content().string(containsString("Start SIP from ₹500")));
        mvc.perform(get("/invest/funds/999")).andExpect(redirectedUrl("/invest"));
        mvc.perform(get("/market/api/funds/122639").param("range", "M6")).andExpect(status().isOk())
                .andExpect(jsonPath("$[-1:].value").value(org.hamcrest.Matchers.hasItem(100.0)));
        mvc.perform(get("/market/api/funds/122639/backtest").param("mode", "lumpsum").param("amount", "10000").param("years", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invested").value(10000));
        mvc.perform(get("/market/api/funds/1/backtest").param("amount", "1000")).andExpect(status().isBadRequest());
        // The home page links to it
        mvc.perform(get("/")).andExpect(content().string(containsString("href=\"/invest\"")));
    }

    @Test
    void backtestUsesRealNavOnEachPurchaseDate() {
        // One-time ₹10,000 a year ago: NAV then was 100 - 0.01 × days(1 year)
        MarketDataService.Backtest lump = market.backtest(122639, "lumpsum", BigDecimal.valueOf(10_000), 1);
        long days = lump.from().until(lump.to(), java.time.temporal.ChronoUnit.DAYS);
        BigDecimal navThen = new BigDecimal("100.00").subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(days)));
        assertThat(lump.units()).isEqualByComparingTo(BigDecimal.valueOf(10_000).divide(navThen, 4, java.math.RoundingMode.DOWN));
        assertThat(lump.gain()).isPositive();

        MarketDataService.Backtest sip = market.backtest(122639, "sip", BigDecimal.valueOf(1_000), 3);
        assertThat(sip.invested()).isEqualByComparingTo("36000");
        assertThat(sip.value()).isGreaterThan(sip.invested());

        assertThatThrownBy(() -> market.backtest(122639, "sip", BigDecimal.valueOf(1_000), 10)).hasMessageContaining("history");
        assertThatThrownBy(() -> market.backtest(122639, "sip", BigDecimal.valueOf(50), 1)).hasMessageContaining("amount");
    }

    @Test
    void investNowAsksForLoginThenReturnsToTheFund() throws Exception {
        OpenedAccount a = accounts.active(10_000);
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get("/customer/invest/funds/122639").session(session)).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login").session(session)).andExpect(content().string(containsString("Log in to invest")));
        mvc.perform(post("/customer/login").session(session).with(csrf())
                        .param("customerId", a.customerId()).param("password", TestAccounts.PASSWORD))
                .andExpect(header().string("Location", containsString("/customer/invest/funds/122639")));
        // A normal login still goes to the dashboard
        MockHttpSession plain = new MockHttpSession();
        mvc.perform(post("/customer/login").session(plain).with(csrf())
                        .param("customerId", a.customerId()).param("password", TestAccounts.PASSWORD))
                .andExpect(redirectedUrl("/customer"));
    }
}
