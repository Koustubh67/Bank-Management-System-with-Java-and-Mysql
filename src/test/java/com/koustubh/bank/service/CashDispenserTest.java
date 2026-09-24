package com.koustubh.bank.service;

import com.koustubh.bank.service.CashDispenser.NoteBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CashDispenserTest {

    @Test
    void usesFewestNotes() {
        assertThat(CashDispenser.breakdown(new BigDecimal("1800")))
                .containsExactly(new NoteBundle(500, 3), new NoteBundle(200, 1), new NoteBundle(100, 1));
        assertThat(CashDispenser.breakdown(new BigDecimal("100"))).containsExactly(new NoteBundle(100, 1));
        assertThat(CashDispenser.breakdown(new BigDecimal("400"))).containsExactly(new NoteBundle(200, 2));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 300, 700, 2500, 9900, 25000})
    void notesAlwaysAddUpToTheAmount(int amount) {
        int total = CashDispenser.breakdown(BigDecimal.valueOf(amount)).stream()
                .mapToInt(n -> n.value() * n.count()).sum();
        assertThat(total).isEqualTo(amount);
    }

    @Test
    void rejectsAmountsThatCannotBePaidInNotes() {
        assertThatThrownBy(() -> CashDispenser.breakdown(new BigDecimal("150")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
