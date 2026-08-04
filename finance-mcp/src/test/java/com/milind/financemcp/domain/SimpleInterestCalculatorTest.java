package com.milind.financemcp.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SimpleInterestCalculatorTest {

    private final SimpleInterestCalculator calculator = new SimpleInterestCalculator();

    // ── happy path ──────────────────────────────────────────────────────────

    @Test
    void calculate_typicalValues_returnsCorrectInterestAndTotal() {
        var result = calculator.calculate(
                new BigDecimal("10000"), new BigDecimal("7.5"), new BigDecimal("3"));

        assertThat(result.interest()).isEqualByComparingTo("2250.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("12250.00");
    }

    @Test
    void calculate_echoesInputsInResult() {
        var result = calculator.calculate(
                new BigDecimal("5000"), new BigDecimal("6"), new BigDecimal("2"));

        assertThat(result.principal()).isEqualByComparingTo("5000.00");
        assertThat(result.annualRatePercent()).isEqualByComparingTo("6");
        assertThat(result.years()).isEqualByComparingTo("2");
    }

    @Test
    void calculate_decimalPrincipal_handledWithoutFloatDrift() {
        var result = calculator.calculate(
                new BigDecimal("1234.56"), new BigDecimal("5.5"), new BigDecimal("1"));

        assertThat(result.interest()).isEqualByComparingTo("67.90");
        assertThat(result.totalAmount()).isEqualByComparingTo("1302.46");
    }

    @Test
    void calculate_fractionalYears_roundsHalfUp() {
        var result = calculator.calculate(
                new BigDecimal("1000"), new BigDecimal("10"), new BigDecimal("0.5"));

        assertThat(result.interest()).isEqualByComparingTo("50.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("1050.00");
    }

    @Test
    void calculate_resultScaledToTwoDecimalPlaces() {
        var result = calculator.calculate(
                new BigDecimal("100"), new BigDecimal("3"), new BigDecimal("1"));

        assertThat(result.interest().scale()).isEqualTo(2);
        assertThat(result.totalAmount().scale()).isEqualTo(2);
    }

    @Test
    void calculate_largeValues_noOverflow() {
        var result = calculator.calculate(
                new BigDecimal("1000000"), new BigDecimal("12"), new BigDecimal("30"));

        assertThat(result.interest()).isEqualByComparingTo("3600000.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("4600000.00");
    }

    // ── principal validation ────────────────────────────────────────────────

    @Test
    void calculate_zeroPrincipal_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO, new BigDecimal("5"), BigDecimal.ONE))
                .withMessageContaining("principal");
    }

    @Test
    void calculate_negativePrincipal_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("-100"), new BigDecimal("5"), BigDecimal.ONE))
                .withMessageContaining("principal");
    }

    @Test
    void calculate_nullPrincipal_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        null, new BigDecimal("5"), BigDecimal.ONE))
                .withMessageContaining("principal");
    }

    // ── rate validation ─────────────────────────────────────────────────────

    @Test
    void calculate_zeroRate_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ONE))
                .withMessageContaining("annualRatePercent");
    }

    @Test
    void calculate_negativeRate_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("1000"), new BigDecimal("-5"), BigDecimal.ONE))
                .withMessageContaining("annualRatePercent");
    }

    @Test
    void calculate_nullRate_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("1000"), null, BigDecimal.ONE))
                .withMessageContaining("annualRatePercent");
    }

    // ── years validation ────────────────────────────────────────────────────

    @Test
    void calculate_zeroYears_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("1000"), new BigDecimal("5"), BigDecimal.ZERO))
                .withMessageContaining("years");
    }

    @Test
    void calculate_negativeYears_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("1000"), new BigDecimal("5"), new BigDecimal("-1")))
                .withMessageContaining("years");
    }

    @Test
    void calculate_nullYears_throwsIllegalArgumentException() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        new BigDecimal("1000"), new BigDecimal("5"), null))
                .withMessageContaining("years");
    }
}
