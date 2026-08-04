package com.yourorg.generalpurposemcp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

/**
 * Simple interest: SI = (P × R × T) / 100.
 * BigDecimal because this is money — no double drift.
 */
@Service
public class SimpleInterestCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public SimpleInterestResult calculate(BigDecimal principal, BigDecimal annualRatePercent, BigDecimal years) {
        requirePositive(principal, "principal");
        requirePositive(annualRatePercent, "annualRatePercent");
        requirePositive(years, "years");

        BigDecimal interest = principal
                .multiply(annualRatePercent)
                .multiply(years)
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);

        return new SimpleInterestResult(
                principal.setScale(2, RoundingMode.HALF_UP),
                annualRatePercent,
                years,
                interest,
                principal.add(interest).setScale(2, RoundingMode.HALF_UP));
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be a positive number");
        }
    }

    /** Domain result model — persistence/transport agnostic. */
    public record SimpleInterestResult(
            BigDecimal principal,
            BigDecimal annualRatePercent,
            BigDecimal years,
            BigDecimal interest,
            BigDecimal totalAmount) {
    }
}
