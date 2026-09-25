package com.koustubh.bank.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    private final Money money = new Money();

    @Test
    void amountsInWordsUseIndianGrouping() {
        assertThat(money.words(new BigDecimal("12382.34"))).isEqualTo("Rupees Twelve Thousand Three Hundred Eighty Two and Thirty Four Paise Only");
        assertThat(money.words(new BigDecimal("10000000"))).isEqualTo("Rupees One Crore Only");
        assertThat(money.words(new BigDecimal("123456789.05")))
                .isEqualTo("Rupees Twelve Crore Thirty Four Lakh Fifty Six Thousand Seven Hundred Eighty Nine and Five Paise Only");
        assertThat(money.words(new BigDecimal("100100.00"))).isEqualTo("Rupees One Lakh One Hundred Only");
        assertThat(money.words(new BigDecimal("0.50"))).isEqualTo("Rupees Zero and Fifty Paise Only");
        assertThat(money.words(new BigDecimal("19.999"))).isEqualTo("Rupees Twenty Only");
    }
}
