package com.koustubh.bank.service;

import com.koustubh.bank.demo.DemoDataSeeder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCardNumbersTest {

    @Test
    void demoCardNumbersAreRealLookingLuhnValidCards() {
        DemoDataSeeder.ALL.forEach(d -> {
            assertThat(d.cardNumber()).hasSize(16).startsWith(NumberGenerator.CARD_PREFIX);
            assertThat(NumberGenerator.isValidLuhn(d.cardNumber())).as(d.name()).isTrue();
        });
    }
}
