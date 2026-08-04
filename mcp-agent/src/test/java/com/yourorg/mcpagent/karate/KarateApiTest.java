package com.yourorg.mcpagent.karate;

import java.util.List;

import com.yourorg.mcpagent.domain.OrchestrationService;
import com.yourorg.mcpagent.domain.OrchestrationService.DiscoveredTool;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Karate API tests for mcp-agent endpoints.
 *
 * Runs with the "karate" profile (application-karate.yml) which disables
 * MCP client connections and Ollama. OrchestrationService is replaced with a
 * Mockito mock so tests are fully self-contained — no external services needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("karate")
class KarateApiTest {

    /** Provides an empty ToolCallbackProvider so ChatClientConfig can build the ChatClient bean. */
    @TestConfiguration
    static class MockToolConfig {
        @Bean
        @Primary
        ToolCallbackProvider toolCallbackProvider() {
            return () -> new org.springframework.ai.tool.ToolCallback[0];
        }
    }

    @MockitoBean
    OrchestrationService orchestrationService;

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        when(orchestrationService.orchestrate(anyString()))
                .thenReturn("Mocked agent response for testing");

        when(orchestrationService.discoverTools())
                .thenReturn(List.of(
                        new DiscoveredTool("finance-mcp",  "calculate_simple_interest",  "Calculates simple interest"),
                        new DiscoveredTool("research-mcp", "search_learning_resources",  "Finds learning resources"),
                        new DiscoveredTool("comms-mcp",    "send_greeting",              "Sends a greeting")
                ));
    }

    @Test
    void allKarateScenarios() {
        Results results = Runner
                .path("classpath:karate")
                .systemProperty("server.port", String.valueOf(port))
                .parallel(1);

        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}
