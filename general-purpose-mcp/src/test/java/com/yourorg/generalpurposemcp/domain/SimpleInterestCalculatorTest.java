package com.yourorg.generalpurposemcp.domain;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SimpleInterestCalculatorTest {

    private final SimpleInterestCalculator calculator = new SimpleInterestCalculator();

    @Test
    void calculatesInterestAndTotal() {
        var result = calculator.calculate(
                new BigDecimal("10000"), new BigDecimal("7.5"), new BigDecimal("3"));

        assertThat(result.interest()).isEqualByComparingTo("2250.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("12250.00");
    }

    @Test
    void rejectsNonPositivePrincipal() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.ZERO, new BigDecimal("5"), BigDecimal.ONE))
                .withMessageContaining("principal");
    }

    @Test
    void rejectsNullRate() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calculator.calculate(
                        BigDecimal.TEN, null, BigDecimal.ONE))
                .withMessageContaining("annualRatePercent");
    }
}
