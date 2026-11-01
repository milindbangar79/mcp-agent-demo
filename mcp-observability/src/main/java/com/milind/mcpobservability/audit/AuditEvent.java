package com.milind.mcpobservability.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One immutable audit record. {@code attributes} already has the content policy applied.
 */
public record AuditEvent(
        UUID id,
        Instant timestamp,
        String service,
        String eventType,
        String traceId,
        String spanId,
        String actor,
        String resource,
        String outcome,
        long durationMs,
        Map<String, Object> attributes) {
}
