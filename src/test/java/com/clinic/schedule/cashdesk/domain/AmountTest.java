package com.clinic.schedule.cashdesk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException.Reason;

class AmountTest {

    private static final AmountCeiling CEILING = new AmountCeiling(new BigDecimal("99999999.99"));

    @Test
    void anAmountIsKeptInPesosAndCents() {
        assertThat(Amount.typed(new BigDecimal("1500"), CEILING).value()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(Amount.typed(new BigDecimal("0.01"), CEILING).value()).isEqualTo(new BigDecimal("0.01"));
        assertThat(Amount.typed(new BigDecimal("1500.500"), CEILING).value()).isEqualTo(new BigDecimal("1500.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "10.001", "0.005"})
    void anAmountIsGreaterThanZeroWithTwoDecimalsAtMost(String value) {
        assertThatThrownBy(() -> Amount.typed(new BigDecimal(value), CEILING))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_AMOUNT));
    }

    @Test
    void anAmountIsSomething() {
        assertThatThrownBy(() -> Amount.typed(null, CEILING))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_AMOUNT));
    }

    @Test
    void aTypedAmountReachesTheCeilingAndNotBeyond() {
        assertThat(Amount.typed(new BigDecimal("99999999.99"), CEILING).value())
                .isEqualTo(new BigDecimal("99999999.99"));

        assertThatThrownBy(() -> Amount.typed(new BigDecimal("100000000"), CEILING))
                .isInstanceOfSatisfying(ChargeRuleViolationException.class, e -> {
                    assertThat(e.reason()).isEqualTo(Reason.AMOUNT_TOO_HIGH);
                    assertThat(e.getMessage()).endsWith(" 99999999.99");
                });
    }

    @Test
    void anAmountReadBackIsNotHeldToTheCeiling() {
        // Lowering the setting does not undo what was charged under the old one.
        assertThat(new Amount(new BigDecimal("100000000")).value()).isEqualTo(new BigDecimal("100000000.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000"})
    void theCeilingFitsTheColumn(String value) {
        assertThatThrownBy(() -> new AmountCeiling(new BigDecimal(value))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCeilingMayBeTheColumnsLargestAmount() {
        assertThat(new AmountCeiling(new BigDecimal("9999999999.99")).value()).isEqualTo(AmountCeiling.COLUMN_MAX);
    }
}
