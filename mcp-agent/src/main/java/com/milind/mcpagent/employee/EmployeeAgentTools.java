package com.milind.mcpagent.employee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Lets the LLM delegate employee questions to employee-agent over REST.
 * Every call carries the shared secret in the X-API-Key header.
 */
@Component
public class EmployeeAgentTools {

    static final String API_KEY_HEADER = "X-API-Key";

    private static final Logger log = LoggerFactory.getLogger(EmployeeAgentTools.class);

    private final RestClient restClient;

    public EmployeeAgentTools(
            RestClient.Builder builder,
            @Value("${employee-agent.url}") String baseUrl,
            @Value("${employee-agent.api-key}") String apiKey) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader(API_KEY_HEADER, apiKey).build();
    }

    @Tool(name = "ask_employee_agent",
            description = "Delegates an employee question to the employee-agent, which looks employees up "
                    + "(by id, date of birth, region, country, state) and returns their full details including salary. "
                    + "Use this first whenever employee data is needed, then pass the returned salaries to finance tools.")
    public String askEmployeeAgent(
            @ToolParam(description = "Natural-language employee question, e.g. 'List all employees in India'")
            String question) {
        log.info("Delegating to employee-agent");
        var response = restClient.post()
                .uri("/api/employee/ask")
                .body(new AskRequest(question))
                .retrieve()
                .body(AskResponse.class);
        return response == null ? "" : response.answer();
    }

    record AskRequest(String question) {}

    record AskResponse(String answer) {}
}
