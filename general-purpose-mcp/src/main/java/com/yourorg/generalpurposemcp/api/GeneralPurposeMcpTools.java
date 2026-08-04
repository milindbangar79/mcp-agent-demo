package com.yourorg.generalpurposemcp.api;

import java.math.BigDecimal;

import com.yourorg.generalpurposemcp.domain.GreetingService;
import com.yourorg.generalpurposemcp.domain.LearningResourcesService;
import com.yourorg.generalpurposemcp.domain.LearningResourcesService.LearningResources;
import com.yourorg.generalpurposemcp.domain.SimpleInterestCalculator;
import com.yourorg.generalpurposemcp.domain.SimpleInterestCalculator.SimpleInterestResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool surface of this service (api layer — the MCP equivalent of a controller).
 *
 * Spring AI's annotation scanner discovers {@link McpTool} methods at startup,
 * generates the JSON schema from the signatures, and registers them with the
 * MCP server automatically. No manual callback wiring.
 */
@Component
public class GeneralPurposeMcpTools {

    private static final Logger log = LoggerFactory.getLogger(GeneralPurposeMcpTools.class);

    private final GreetingService greetingService;
    private final SimpleInterestCalculator interestCalculator;
    private final LearningResourcesService learningResourcesService;

    public GeneralPurposeMcpTools(GreetingService greetingService,
                                  SimpleInterestCalculator interestCalculator,
                                  LearningResourcesService learningResourcesService) {
        this.greetingService = greetingService;
        this.interestCalculator = interestCalculator;
        this.learningResourcesService = learningResourcesService;
    }

    @McpTool(
            name = "send_greeting",
            description = "Sends a friendly greeting. If a name is provided, the greeting is "
                    + "personalized; otherwise it returns a generic 'Hello, World!' message.")
    public String sendGreeting(
            @McpToolParam(description = "Optional name of the person to greet", required = false)
            String name) {
        log.info("Tool invoked: send_greeting");
        return greetingService.greet(name);
    }

    @McpTool(
            name = "search_learning_resources",
            description = "Returns curated online calculators, reference articles, and training content "
                    + "for a given financial topic. Call this alongside any calculation tool to enrich "
                    + "the response with learning recommendations.")
    public LearningResources searchLearningResources(
            @McpToolParam(description = "The financial topic to find resources for, e.g. 'simple interest'", required = true)
            String topic) {
        log.info("Tool invoked: search_learning_resources for topic='{}'", topic);
        return learningResourcesService.findResources(topic);
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
        return interestCalculator.calculate(
                new BigDecimal(principal.trim()),
                new BigDecimal(annualRatePercent.trim()),
                new BigDecimal(years.trim()));
    }
}
