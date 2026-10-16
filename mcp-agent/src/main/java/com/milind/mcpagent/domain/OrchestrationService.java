package com.milind.mcpagent.domain;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Core agent logic: hand the user's prompt to the LLM, which decides which
 * MCP-discovered tools to call (possibly several, possibly none), executes
 * them through the tool-calling loop, and produces the final answer.
 */
@Service
public class OrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationService.class);

    private final ChatClient chatClient;
    private final List<McpSyncClient> mcpClients;

    public OrchestrationService(ChatClient chatClient, List<McpSyncClient> mcpClients) {
        this.chatClient = chatClient;
        this.mcpClients = mcpClients;
    }

    public String orchestrate(String prompt) {
        log.info("Orchestrating prompt via LLM with {} connected MCP client(s)", mcpClients.size());
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * Deterministic discovery: lists every tool exposed by every connected MCP
     * server, straight from {@code tools/list} — no LLM involved. Useful for
     * "what can this agent do" style introspection and for debugging.
     */
    public List<DiscoveredTool> discoverTools() {
        return mcpClients.stream()
                .flatMap(client -> {
                    var serverName = client.getServerInfo() != null
                            ? client.getServerInfo().name()
                            : "unknown";
                    return client.listTools().tools().stream()
                            .map(tool -> new DiscoveredTool(serverName, tool.name(), tool.description()));
                })
                .toList();
    }

    public record DiscoveredTool(String server, String name, String description) {
    }
}
