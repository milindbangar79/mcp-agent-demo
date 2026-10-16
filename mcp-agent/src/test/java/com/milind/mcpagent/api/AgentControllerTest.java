package com.milind.mcpagent.api;

import java.util.List;

import com.milind.mcpagent.domain.OrchestrationService;
import com.milind.mcpagent.domain.OrchestrationService.DiscoveredTool;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller unit tests using MockMvc standalone setup.
 *
 * No Spring context is loaded — standaloneSetup wires only the controller with
 * Jackson message converters. Spring Boot 4 removed @WebMvcTest; this approach
 * is faster and avoids all Spring AI auto-configuration concerns.
 */
class AgentControllerTest {

    private final OrchestrationService orchestrationService = mock(OrchestrationService.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AgentController(orchestrationService))
            .build();

    // ── POST /api/agent/chat ─────────────────────────────────────────────

    @Test
    void chat_validPrompt_returns200WithAnswer() throws Exception {
        when(orchestrationService.orchestrate(anyString()))
                .thenReturn("The simple interest is 2250.00");

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "Calculate SI on 10000 at 7.5% for 3 years"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The simple interest is 2250.00"));
    }

    @Test
    void chat_blankPrompt_returns400WithMessage() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.answer").value("prompt must not be blank"));
    }

    @Test
    void chat_emptyStringPrompt_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.answer").value("prompt must not be blank"));
    }

    @Test
    void chat_nullPromptField_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.answer").value("prompt must not be blank"));
    }

    @Test
    void chat_missingPromptField_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-valid-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_responseContainsAnswerField() throws Exception {
        when(orchestrationService.orchestrate(anyString())).thenReturn("Hello, World!");

        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt": "Greet me"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    // ── GET /api/agent/tools ─────────────────────────────────────────────

    @Test
    void tools_returns200WithToolArray() throws Exception {
        when(orchestrationService.discoverTools()).thenReturn(List.of(
                new DiscoveredTool("finance-mcp",  "calculate_simple_interest",  "Calculates SI"),
                new DiscoveredTool("research-mcp", "search_learning_resources",  "Finds resources"),
                new DiscoveredTool("comms-mcp",    "send_greeting",              "Sends greeting")
        ));

        mockMvc.perform(get("/api/agent/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void tools_eachEntryHasServerNameAndDescription() throws Exception {
        when(orchestrationService.discoverTools()).thenReturn(List.of(
                new DiscoveredTool("finance-mcp", "calculate_simple_interest", "Calculates SI")
        ));

        mockMvc.perform(get("/api/agent/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].server").value("finance-mcp"))
                .andExpect(jsonPath("$[0].name").value("calculate_simple_interest"))
                .andExpect(jsonPath("$[0].description").value("Calculates SI"));
    }

    @Test
    void tools_returnsEmptyArrayWhenNoToolsDiscovered() throws Exception {
        when(orchestrationService.discoverTools()).thenReturn(List.of());

        mockMvc.perform(get("/api/agent/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void tools_doesNotRequireRequestBody() throws Exception {
        when(orchestrationService.discoverTools()).thenReturn(List.of());

        mockMvc.perform(get("/api/agent/tools"))
                .andExpect(status().isOk());
    }
}
