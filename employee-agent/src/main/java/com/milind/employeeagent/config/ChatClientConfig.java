package com.milind.employeeagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    static final String SYSTEM_PROMPT = """
            You are the employee agent. Another agent (mcp-agent) delegates employee questions to you.
            You have tools from employee-mcp: get_employee_details (one employee by id) and
            search_employees (any combination of id, date of birth or range, region, country, state).

            Rules:
            - Always use the tools to answer; never invent employee data.
            - Return the matching employees' data completely and exactly as returned by the tools,
              including employeeId, name, dateOfBirth, region, country, state and salary, in a compact
              list so the calling agent can use it for further processing.
            - If no employee matches, say so explicitly. If a tool errors (for example a rate limit),
              report the error text; do not guess.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider mcpToolCallbackProvider) {
        return builder.defaultSystem(SYSTEM_PROMPT).defaultTools(mcpToolCallbackProvider).build();
    }
}
