package com.milind.financemcp.api;

import java.math.BigDecimal;

import com.milind.financemcp.domain.SimpleInterestCalculator;
import com.milind.financemcp.domain.SimpleInterestCalculator.SimpleInterestResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class FinanceMcpTools {

    private static final Logger log = LoggerFactory.getLogger(FinanceMcpTools.class);

    private final SimpleInterestCalculator calculator;

    public FinanceMcpTools(SimpleInterestCalculator calculator) {
        this.calculator = calculator;
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
        log.info("Tool invoked: calculate_simple_interest");
        return calculator.calculate(
                new BigDecimal(principal.trim()),
                new BigDecimal(annualRatePercent.trim()),
                new BigDecimal(years.trim()));
    }
}
