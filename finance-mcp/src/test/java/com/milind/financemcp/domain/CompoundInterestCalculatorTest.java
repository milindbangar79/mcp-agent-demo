package com.milind.financemcp.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import com.milind.financemcp.domain.BatchCompoundInterestService.Item;

import org.junit.jupiter.api.Test;

class CompoundInterestCalculatorTest {

    private final CompoundInterestCalculator calc = new CompoundInterestCalculator();

    @Test
    void annualCompounding() {
        var r = calc.calculate(new BigDecimal("10000"), new BigDecimal("10"), new BigDecimal("2"), 1);
        assertThat(r.totalAmount()).isEqualByComparingTo("12100.00");
        assertThat(r.interest()).isEqualByComparingTo("2100.00");
    }

    @Test
    void quarterlyCompounding() {
        var r = calc.calculate(new BigDecimal("1000"), new BigDecimal("8"), new BigDecimal("1"), 4);
        assertThat(r.totalAmount()).isEqualByComparingTo("1082.43");
    }

    @Test
    void fractionalPeriodsRejected() {
        assertThatThrownBy(() -> calc.calculate(new BigDecimal("1000"), new BigDecimal("8"), new BigDecimal("1.5"), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveInputsRejected() {
        assertThatThrownBy(() -> calc.calculate(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calc.calculate(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void batchSumsTotals() {
        var svc = new BatchCompoundInterestService(calc);
        var r = svc.calculate(List.of(new Item("E001", "10000"), new Item("E002", "20000")),
                new BigDecimal("10"), new BigDecimal("2"), 1);
        assertThat(r.entries()).hasSize(2);
        assertThat(r.totalInterest()).isEqualByComparingTo("6300.00");
        assertThat(r.totalAmount()).isEqualByComparingTo("36300.00");
    }

    @Test
    void emptyBatchRejected() {
        assertThatThrownBy(() -> new BatchCompoundInterestService(calc)
                .calculate(List.of(), BigDecimal.ONE, BigDecimal.ONE, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
