package com.milind.mcpobservability.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.milind.mcpobservability.audit.AuditRecorder;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Taps Spring AI's own observations to build the audit trail of the LLM flow. Two handlers:
 * <ul>
 *   <li>{@link Chat}: every model round-trip (the tool-calling loop makes several per question) with the full
 *       prompt, the completion, the requested tool calls, token usage and finish reason;</li>
 *   <li>{@link Tool}: every tool the model invoked (local {@code @Tool} and MCP-discovered), with arguments/result.</li>
 * </ul>
 * Prompt/completion/arguments go only to the audit sinks. Spring AI's span-level content logging stays off.
 */
public final class LlmAuditObservationHandlers {

    private static final Logger log = LoggerFactory.getLogger(LlmAuditObservationHandlers.class);
    private static final String START_NANOS = "mcp.audit.startNanos";

    private LlmAuditObservationHandlers() {
    }

    private static long elapsedMs(Observation.Context ctx) {
        Long start = ctx.get(START_NANOS);
        return start == null ? 0 : (System.nanoTime() - start) / 1_000_000;
    }

    /** The tracing handler stores the span in the context on start; we read it back on stop. */
    private static String[] traceIds(Observation.Context ctx) {
        TracingObservationHandler.TracingContext tc = ctx.get(TracingObservationHandler.TracingContext.class);
        if (tc == null || tc.getSpan() == null) {
            return new String[] {null, null};
        }
        return new String[] {tc.getSpan().context().traceId(), tc.getSpan().context().spanId()};
    }

    public static final class Chat implements ObservationHandler<ChatModelObservationContext> {

        private final AuditRecorder audit;
        private final JsonMapper mapper;

        public Chat(AuditRecorder audit, JsonMapper mapper) {
            this.audit = audit;
            this.mapper = mapper;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ChatModelObservationContext;
        }

        @Override
        public void onStart(ChatModelObservationContext ctx) {
            ctx.put(START_NANOS, System.nanoTime());
        }

        @Override
        public void onStop(ChatModelObservationContext ctx) {
            try {
                String[] ids = traceIds(ctx);
                ChatResponse response = ctx.getResponse();
                var draft = audit.event("llm.call")
                        .resource(ctx.getRequest() != null && ctx.getRequest().getOptions() != null
                                ? String.valueOf(ctx.getRequest().getOptions().getModel()) : "unknown")
                        .outcome(ctx.getError() == null ? "success" : "error")
                        .durationMs(elapsedMs(ctx))
                        .trace(ids[0], ids[1])
                        .meta("streaming", ctx.isStreaming());
                if (ctx.getOperationMetadata() != null) {
                    draft.meta("provider", ctx.getOperationMetadata().provider());
                }
                if (ctx.getRequest() != null) {
                    draft.content("prompt", mapper.writeValueAsString(messages(ctx.getRequest().getInstructions())));
                }
                if (response != null) {
                    var meta = response.getMetadata();
                    if (meta != null) {
                        draft.meta("responseModel", meta.getModel());
                        if (meta.getUsage() != null) {
                            draft.meta("inputTokens", meta.getUsage().getPromptTokens());
                            draft.meta("outputTokens", meta.getUsage().getCompletionTokens());
                        }
                    }
                    if (response.getResult() != null) {
                        AssistantMessage out = response.getResult().getOutput();
                        draft.content("completion", out.getText());
                        if (out.hasToolCalls()) {
                            draft.content("requestedToolCalls", mapper.writeValueAsString(out.getToolCalls()));
                        }
                        if (response.getResult().getMetadata() != null) {
                            draft.meta("finishReason", response.getResult().getMetadata().getFinishReason());
                        }
                    }
                }
                if (ctx.getError() != null) {
                    draft.meta("errorType", ctx.getError().getClass().getSimpleName());
                    draft.content("errorMessage", ctx.getError().getMessage());
                }
                draft.commit();
            } catch (RuntimeException e) {
                log.error("Failed to audit LLM call", e);
            }
        }

        private static List<Map<String, Object>> messages(List<Message> instructions) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Message m : instructions) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("role", m.getMessageType().getValue());
                if (m.getText() != null && !m.getText().isEmpty()) {
                    entry.put("text", m.getText());
                }
                if (m instanceof ToolResponseMessage trm) {
                    entry.put("toolResponses", trm.getResponses());
                }
                if (m instanceof AssistantMessage am && am.hasToolCalls()) {
                    entry.put("toolCalls", am.getToolCalls());
                }
                out.add(entry);
            }
            return out;
        }
    }

    /**
     * Spring AI's MCP tool callback turns an MCP error result (invalid arguments, rate limit, ...) into the
     * plain string "Error calling tool: ..." instead of throwing, so the observation itself ends OK and the
     * span stays green. Flag it in the audit trail. This is a prefix heuristic on Spring AI's wording.
     */
    static String toolOutcome(Throwable error, String result) {
        if (error != null) {
            return "error";
        }
        return result != null && result.startsWith("Error calling tool") ? "tool_error" : "success";
    }

    public static final class Tool implements ObservationHandler<ToolCallingObservationContext> {

        private final AuditRecorder audit;

        public Tool(AuditRecorder audit) {
            this.audit = audit;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ToolCallingObservationContext;
        }

        @Override
        public void onStart(ToolCallingObservationContext ctx) {
            ctx.put(START_NANOS, System.nanoTime());
        }

        @Override
        public void onStop(ToolCallingObservationContext ctx) {
            try {
                String[] ids = traceIds(ctx);
                var draft = audit.event("llm.tool.call")
                        .resource(ctx.getToolDefinition().name())
                        .outcome(toolOutcome(ctx.getError(), ctx.getToolCallResult()))
                        .durationMs(elapsedMs(ctx))
                        .trace(ids[0], ids[1])
                        .meta("toolCallId", ctx.getToolCallId())
                        .meta("toolType", ctx.getToolType())
                        .content("arguments", ctx.getToolCallArguments())
                        .content("result", ctx.getToolCallResult());
                if (ctx.getError() != null) {
                    draft.meta("errorType", ctx.getError().getClass().getSimpleName());
                    draft.content("errorMessage", ctx.getError().getMessage());
                }
                draft.commit();
            } catch (RuntimeException e) {
                log.error("Failed to audit tool call", e);
            }
        }
    }
}
