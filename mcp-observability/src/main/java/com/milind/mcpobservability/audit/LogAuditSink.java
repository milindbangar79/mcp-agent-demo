package com.milind.mcpobservability.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes each event as one JSON line on the {@code mcp.audit} logger. With OTLP log export on, that is what
 * lands in Loki (query: {@code {service_name="mcp-agent"} | json | event_type="llm.call"}). Developer-debugging
 * sink: durable only as far as the log pipeline, and not tamper-evident.
 */
public class LogAuditSink implements AuditSink {

    private static final Logger audit = LoggerFactory.getLogger("mcp.audit");

    private final JsonMapper mapper;

    public LogAuditSink(JsonMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(AuditEvent e) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("event_id", e.id().toString());
        line.put("ts", e.timestamp().toString());
        line.put("service", e.service());
        line.put("event_type", e.eventType());
        line.put("trace_id", e.traceId());
        line.put("span_id", e.spanId());
        line.put("actor", e.actor());
        line.put("resource", e.resource());
        line.put("outcome", e.outcome());
        line.put("duration_ms", e.durationMs());
        line.put("attributes", e.attributes());
        audit.info(mapper.writeValueAsString(line));
    }
}
