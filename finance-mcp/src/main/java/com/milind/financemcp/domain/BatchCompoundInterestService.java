package com.milind.financemcp.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import com.milind.financemcp.domain.CompoundInterestCalculator.CompoundInterestResult;

import org.springframework.stereotype.Service;

/** Applies one rate/term to many principals (e.g. a group of employees' salaries). */
@Service
public class BatchCompoundInterestService {

    static final int MAX_ITEMS = 500;

    private final CompoundInterestCalculator calculator;

    public BatchCompoundInterestService(CompoundInterestCalculator calculator) {
        this.calculator = calculator;
    }

    public BatchResult calculate(
            List<Item> items, BigDecimal annualRatePercent, BigDecimal years, int compoundsPerYear) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must contain at least one entry");
        }
        if (items.size() > MAX_ITEMS) {
            throw new IllegalArgumentException("items must contain at most " + MAX_ITEMS + " entries");
        }
        var results = items.stream()
                .map(i -> new EntryResult(i.id(), calculator.calculate(
                        new BigDecimal(i.principal().trim()), annualRatePercent, years, compoundsPerYear)))
                .toList();
        BigDecimal totalInterest = results.stream().map(r -> r.result().interest())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = results.stream().map(r -> r.result().totalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        return new BatchResult(results, totalInterest, totalAmount);
    }

    public record Item(String id, String principal) {}

    public record EntryResult(String id, CompoundInterestResult result) {}

    public record BatchResult(List<EntryResult> entries, BigDecimal totalInterest, BigDecimal totalAmount) {}
}
