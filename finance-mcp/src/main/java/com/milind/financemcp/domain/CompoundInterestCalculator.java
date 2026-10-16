package com.milind.financemcp.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

/** Compound interest: A = P × (1 + r/(100·n))^(n·t). BigDecimal throughout. */
@Service
public class CompoundInterestCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public CompoundInterestResult calculate(
            BigDecimal principal, BigDecimal annualRatePercent, BigDecimal years, int compoundsPerYear) {
        requirePositive(principal, "principal");
        requirePositive(annualRatePercent, "annualRatePercent");
        requirePositive(years, "years");
        if (compoundsPerYear <= 0) {
            throw new IllegalArgumentException("compoundsPerYear must be a positive integer");
        }

        BigDecimal periods = years.multiply(BigDecimal.valueOf(compoundsPerYear));
        if (periods.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "years x compoundsPerYear must be a whole number of compounding periods, got " + periods.toPlainString());
        }
        int n = periods.intValueExact();

        BigDecimal ratePerPeriod = annualRatePercent.divide(HUNDRED.multiply(BigDecimal.valueOf(compoundsPerYear)), MathContext.DECIMAL128);
        BigDecimal growth = BigDecimal.ONE.add(ratePerPeriod).pow(n, MathContext.DECIMAL128);
        BigDecimal total = principal.multiply(growth).setScale(2, RoundingMode.HALF_UP);
        BigDecimal p = principal.setScale(2, RoundingMode.HALF_UP);

        return new CompoundInterestResult(p, annualRatePercent, years, compoundsPerYear, total.subtract(p), total);
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive number");
        }
    }

    public record CompoundInterestResult(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            BigDecimal years,
            int compoundsPerYear,
            BigDecimal interest,
            BigDecimal totalAmount) {}
}
