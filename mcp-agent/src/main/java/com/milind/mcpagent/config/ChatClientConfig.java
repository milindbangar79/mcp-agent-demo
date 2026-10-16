package com.milind.mcpagent.config;

import com.milind.mcpagent.employee.EmployeeAgentTools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Ollama-backed ChatClient with every tool discovered from the
 * configured MCP servers.
 *
 * The {@link ToolCallbackProvider} bean is autoconfigured by the MCP client
 * starter: at startup it connects to each server declared under
 * {@code spring.ai.mcp.client.streamable-http.connections}, calls
 * {@code tools/list}, and adapts each discovered MCP tool into a Spring AI
 * tool callback. That is the "discovery" half; the LLM's tool-calling loop
 * is the "orchestration" half.
 */
@Configuration
public class ChatClientConfig {

    static final String SYSTEM_PROMPT = """
            You are an orchestration agent. You have access to tools discovered \
            dynamically from remote MCP servers.

            Rules:
            - Whenever a user request can be fulfilled by one of your tools, call the \
              tool rather than answering from memory. Chain multiple tool calls in one \
              turn when the request needs it.
            - For any calculation, always call the relevant tool; never compute it yourself.
            - Whenever you call a calculation tool, also call search_learning_resources \
              with the relevant topic (e.g. "simple interest") and include ALL returned \
              items in your response verbatim — do not filter, summarize, or omit any. \
              Organize them under three headings: Online Calculators, Articles, and \
              Training Content. For each item list its name, URL, and description.
            - Employee data: call ask_employee_agent (delegates to employee-agent), or the \
              employee-mcp tools get_employee_details / search_employees directly. Never invent \
              employee data.
            - Compound interest for employees: first obtain the employees and their salaries, then \
              use salary as the principal. For ONE employee call calculate_compound_interest; for \
              MANY employees call calculate_compound_interest_batch once with every employee as an \
              item (id = employeeId, principal = salary). If the user did not give the annual rate, \
              years, or compounds per year, ask for the missing value instead of assuming it.
            - If the user asks what you can do, or their request only partially matches \
              a tool, list the available tools with their names and one-line descriptions \
              and suggest which one(s) apply to their request.
            - If no tool applies, say so explicitly and list the tools you do have.
            - Be concise and state which tool(s) you called.
            """;

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider mcpToolCallbackProvider,
                          EmployeeAgentTools employeeAgentTools) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(mcpToolCallbackProvider, employeeAgentTools)
                .build();
    }
}
