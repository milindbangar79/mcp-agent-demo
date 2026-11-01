package com.milind.mcpobservability.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ToolOutcomeTest {

    @Test
    void thrownErrorIsError() {
        assertThat(LlmAuditObservationHandlers.toolOutcome(new IllegalStateException("x"), null)).isEqualTo("error");
    }

    @Test
    void mcpErrorResultReturnedAsTextIsToolError() {
        assertThat(LlmAuditObservationHandlers.toolOutcome(null,
                "Error calling tool: [TextContent[text=Rate limit exceeded]]")).isEqualTo("tool_error");
    }

    @Test
    void normalResultIsSuccess() {
        assertThat(LlmAuditObservationHandlers.toolOutcome(null, "{\"employeeId\":\"E001\"}")).isEqualTo("success");
        assertThat(LlmAuditObservationHandlers.toolOutcome(null, null)).isEqualTo("success");
    }
}
