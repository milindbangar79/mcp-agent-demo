package com.milind.mcpagent.api;

import java.util.List;

import com.milind.mcpagent.domain.OrchestrationService;
import com.milind.mcpagent.domain.OrchestrationService.DiscoveredTool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final OrchestrationService orchestrationService;

    public AgentController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * The LLM-driven path. Examples:
     *   {"prompt": "Greet Priya, and also tell me the simple interest on 10000 at 7.5% for 3 years"}
     *   {"prompt": "What tools do you have?"}
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            // 400: request validation failure per org HTTP status mapping
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("prompt must not be blank"));
        }
        log.info("Received chat request");
        var answer = orchestrationService.orchestrate(request.prompt());
        return ResponseEntity.status(HttpStatus.OK).body(new ChatResponse(answer));
    }

    /** Deterministic tool discovery — straight from MCP tools/list, no LLM. */
    @GetMapping("/tools")
    public List<DiscoveredTool> tools() {
        return orchestrationService.discoverTools();
    }

    public record ChatRequest(String prompt) {
    }

    public record ChatResponse(String answer) {
    }
}
