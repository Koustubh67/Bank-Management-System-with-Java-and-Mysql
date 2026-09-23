package com.koustubh.bank.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class NumberGeneratorTest {

    private final NumberGenerator generator = new NumberGenerator(new SecureRandom());

    @Test
    void luhnCheckDigitMatchesKnownExample() {
        // Standard example from the Luhn algorithm description: 7992739871 -> check digit 3
        assertThat(NumberGenerator.luhnCheckDigit("7992739871")).isEqualTo(3);
        assertThat(NumberGenerator.isValidLuhn("79927398713")).isTrue();
        assertThat(NumberGenerator.isValidLuhn("79927398710")).isFalse();
    }

    @RepeatedTest(20)
    void cardNumbersAre16DigitsWithPrefixAndValidCheckDigit() {
        String card = generator.newCardNumber();
        assertThat(card).hasSize(16).startsWith(NumberGenerator.CARD_PREFIX).containsOnlyDigits();
        assertThat(NumberGenerator.isValidLuhn(card)).isTrue();
    }

    @RepeatedTest(20)
    void accountNumbersAndPinsHaveFixedLength() {
        assertThat(generator.newAccountNumber()).hasSize(12).containsOnlyDigits();
        assertThat(generator.newPin()).hasSize(4).containsOnlyDigits();
    }
}
