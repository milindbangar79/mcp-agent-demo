package com.milind.commsmcp.api;

import com.milind.commsmcp.domain.GreetingService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class CommsMcpTools {

    private static final Logger log = LoggerFactory.getLogger(CommsMcpTools.class);

    private final GreetingService greetingService;

    public CommsMcpTools(GreetingService greetingService) {
        this.greetingService = greetingService;
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
}
