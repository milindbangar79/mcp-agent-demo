package com.milind.mcpobservability.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import com.milind.mcpobservability.audit.AuditRecorder;
import com.milind.mcpratelimit.RateLimitExceededException;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wraps every {@code @McpTool} method on an MCP server. One call produces:
 * <ul>
 *   <li>a span {@code mcp.tool <name>} (child of the inbound HTTP span, so it joins the caller's trace),</li>
 *   <li>a timer {@code mcp.tool.call} tagged {@code mcp.tool}, {@code mcp.server}, {@code outcome}
 *       ({@code success | error | rate_limited}),</li>
 *   <li>an audit event {@code mcp.tool.invoke} with arguments and result (content policy applies).</li>
 * </ul>
 * Arguments and results never go on the span or the metric.
 */
@Aspect
public class McpToolAuditAspect {

    private final ObservationRegistry registry;
    private final AuditRecorder audit;
    private final ObjectProvider<Tracer> tracer;
    private final JsonMapper mapper;
    private final String server;

    public McpToolAuditAspect(ObservationRegistry registry, AuditRecorder audit, ObjectProvider<Tracer> tracer,
                              JsonMapper mapper, String server) {
        this.registry = registry;
        this.audit = audit;
        this.tracer = tracer;
        this.mapper = mapper;
        this.server = server;
    }

    @Around("@annotation(mcpTool)")
    public Object around(ProceedingJoinPoint pjp, McpTool mcpTool) throws Throwable {
        var signature = (MethodSignature) pjp.getSignature();
        String tool = mcpTool.name() == null || mcpTool.name().isBlank() ? signature.getName() : mcpTool.name();

        Observation obs = Observation.createNotStarted("mcp.tool.call", registry)
                .contextualName("mcp.tool " + tool)
                .lowCardinalityKeyValue("mcp.tool", tool)
                .lowCardinalityKeyValue("mcp.server", server)
                .start();
        long t0 = System.nanoTime();
        String outcome = "success";
        Object result = null;
        Throwable failure = null;
        try (Observation.Scope ignored = obs.openScope()) {
            try {
                result = pjp.proceed();
                return result;
            } catch (RateLimitExceededException e) {
                outcome = "rate_limited";
                failure = e;
                throw e;
            } catch (Throwable e) {
                outcome = "error";
                failure = e;
                obs.error(e);
                throw e;
            } finally {
                obs.lowCardinalityKeyValue("outcome", outcome);
                writeAudit(tool, signature, pjp.getArgs(), outcome, result, failure, (System.nanoTime() - t0) / 1_000_000);
            }
        } finally {
            obs.stop();
        }
    }

    private void writeAudit(String tool, MethodSignature signature, Object[] args, String outcome, Object result,
                            Throwable failure, long durationMs) {
        try {
            Map<String, Object> named = new LinkedHashMap<>();
            String[] names = signature.getParameterNames();
            for (int i = 0; i < args.length; i++) {
                if (args[i] != null) {
                    named.put(names != null ? names[i] : "arg" + i, args[i]);
                }
            }
            Tracer t = tracer.getIfAvailable();
            Span span = t == null ? null : t.currentSpan();
            var draft = audit.event("mcp.tool.invoke")
                    .resource(tool)
                    .outcome(outcome)
                    .durationMs(durationMs)
                    .content("arguments", mapper.writeValueAsString(named));
            if (span != null) {
                draft.trace(span.context().traceId(), span.context().spanId());
            }
            if (result != null) {
                draft.content("result", mapper.writeValueAsString(result));
            }
            if (failure != null) {
                draft.meta("errorType", failure.getClass().getSimpleName());
                draft.content("errorMessage", failure.getMessage());
            }
            draft.commit();
        } catch (RuntimeException e) {
            // Auditing must never change the outcome of the tool call.
            org.slf4j.LoggerFactory.getLogger(McpToolAuditAspect.class).error("Failed to audit tool call {}", tool, e);
        }
    }
}
