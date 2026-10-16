package com.milind.financemcp.api;

import java.math.BigDecimal;

import java.util.List;

import com.milind.financemcp.domain.BatchCompoundInterestService;
import com.milind.financemcp.domain.BatchCompoundInterestService.BatchResult;
import com.milind.financemcp.domain.BatchCompoundInterestService.Item;
import com.milind.financemcp.domain.CompoundInterestCalculator;
import com.milind.financemcp.domain.CompoundInterestCalculator.CompoundInterestResult;
import com.milind.financemcp.domain.SimpleInterestCalculator;
import com.milind.financemcp.domain.SimpleInterestCalculator.SimpleInterestResult;

import com.milind.financemcp.ratelimit.SlidingWindowRateLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class FinanceMcpTools {

    private static final Logger log = LoggerFactory.getLogger(FinanceMcpTools.class);

    private final SimpleInterestCalculator calculator;

    private final CompoundInterestCalculator compoundCalculator;
    private final BatchCompoundInterestService batchService;
    private final SlidingWindowRateLimiter rateLimiter;

    public FinanceMcpTools(SimpleInterestCalculator calculator,
                           CompoundInterestCalculator compoundCalculator,
                           BatchCompoundInterestService batchService,
                           SlidingWindowRateLimiter rateLimiter) {
        this.calculator = calculator;
        this.compoundCalculator = compoundCalculator;
        this.batchService = batchService;
        this.rateLimiter = rateLimiter;
    }

    @McpTool(
            name = "calculate_simple_interest",
            description = "Calculates simple interest using SI = (principal * rate * time) / 100. "
                    + "Returns the interest amount and the total amount (principal + interest). "
                    + "All inputs must be positive numbers.")
    public SimpleInterestResult calculateSimpleInterest(
            @McpToolParam(description = "Principal amount, e.g. 10000", required = true)
            String principal,
            @McpToolParam(description = "Annual interest rate in percent, e.g. 7.5", required = true)
            String annualRatePercent,
            @McpToolParam(description = "Duration in years, e.g. 3", required = true)
            String years) {
        rateLimiter.acquireOrThrow();
        log.info("Tool invoked: calculate_simple_interest");
        return calculator.calculate(
                new BigDecimal(principal.trim()),
                new BigDecimal(annualRatePercent.trim()),
                new BigDecimal(years.trim()));
    }

    @McpTool(
            name = "calculate_compound_interest",
            description = "Calculates compound interest for ONE principal: A = P * (1 + r/(100*n))^(n*t). "
                    + "Returns the interest amount and the total amount. years x compoundsPerYear must be a whole number.")
    public CompoundInterestResult calculateCompoundInterest(
            @McpToolParam(description = "Principal amount, e.g. 85000", required = true) String principal,
            @McpToolParam(description = "Annual interest rate in percent, e.g. 7.5", required = true) String annualRatePercent,
            @McpToolParam(description = "Duration in years, e.g. 3", required = true) String years,
            @McpToolParam(description = "Compounding periods per year, e.g. 1 annual, 4 quarterly, 12 monthly", required = true)
            String compoundsPerYear) {
        rateLimiter.acquireOrThrow();
        log.info("Tool invoked: calculate_compound_interest");
        return compoundCalculator.calculate(
                new BigDecimal(principal.trim()),
                new BigDecimal(annualRatePercent.trim()),
                new BigDecimal(years.trim()),
                Integer.parseInt(compoundsPerYear.trim()));
    }

    @McpTool(
            name = "calculate_compound_interest_batch",
            description = "Calculates compound interest for MANY principals (e.g. a group of employees) using the same "
                    + "rate, term and compounding. Each item has an id (e.g. employee id) and a principal (e.g. salary). "
                    + "Returns per-item results plus total interest and total amount. Max 500 items.")
    public BatchResult calculateCompoundInterestBatch(
            @McpToolParam(description = "List of {id, principal} entries", required = true) List<Item> items,
            @McpToolParam(description = "Annual interest rate in percent, e.g. 7.5", required = true) String annualRatePercent,
            @McpToolParam(description = "Duration in years, e.g. 3", required = true) String years,
            @McpToolParam(description = "Compounding periods per year, e.g. 1 annual, 4 quarterly, 12 monthly", required = true)
            String compoundsPerYear) {
        rateLimiter.acquireOrThrow();
        log.info("Tool invoked: calculate_compound_interest_batch");
        return batchService.calculate(
                items,
                new BigDecimal(annualRatePercent.trim()),
                new BigDecimal(years.trim()),
                Integer.parseInt(compoundsPerYear.trim()));
    }
}
