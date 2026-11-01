package com.milind.mcpobservability.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Single entry point for writing audit events: applies the content policy, fills in the trace ids and fans
 * out to every {@link AuditSink}. A failing sink is logged and swallowed so auditing can never break a user
 * request. (For a fail-closed compliance posture this is the one place to change.)
 */
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final String service;
    private final AuditProperties properties;
    private final List<AuditSink> sinks;
    private final ObjectProvider<Tracer> tracer;

    public AuditRecorder(String service, AuditProperties properties, List<AuditSink> sinks,
                         ObjectProvider<Tracer> tracer) {
        this.service = service;
        this.properties = properties;
        this.sinks = List.copyOf(sinks);
        this.tracer = tracer;
    }

    public Draft event(String eventType) {
        return new Draft(eventType);
    }

    /** Applies the content policy to one field and writes the result into {@code target}. */
    void putContent(Map<String, Object> target, String key, String value) {
        if (value == null) {
            return;
        }
        if (properties.content() == AuditProperties.Content.REDACTED) {
            target.put(key + "Sha256", sha256(value));
            target.put(key + "Chars", value.length());
            return;
        }
        int max = properties.maxContentChars();
        if (value.length() > max) {
            target.put(key, value.substring(0, max));
            target.put(key + "Truncated", true);
            target.put(key + "Chars", value.length());
        } else {
            target.put(key, value);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public final class Draft {

        private final String eventType;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private String traceId;
        private String spanId;
        private String actor = "anonymous";
        private String resource = "";
        private String outcome = "success";
        private long durationMs;

        private Draft(String eventType) {
            this.eventType = eventType;
        }

        public Draft actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Draft resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Draft outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public Draft durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Draft trace(String traceId, String spanId) {
            this.traceId = traceId;
            this.spanId = spanId;
            return this;
        }

        /** Non-sensitive metadata (model name, token counts...). Stored as-is under every policy. */
        public Draft meta(String key, Object value) {
            if (value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        /** Potentially sensitive text (prompts, completions, tool arguments/results). Subject to the content policy. */
        public Draft content(String key, String value) {
            putContent(attributes, key, value);
            return this;
        }

        public void commit() {
            if (traceId == null) {
                Tracer t = tracer.getIfAvailable();
                Span span = t == null ? null : t.currentSpan();
                if (span != null) {
                    traceId = span.context().traceId();
                    spanId = span.context().spanId();
                }
            }
            // Microsecond precision: that is what Postgres keeps, and the hash chain must survive a round trip.
            var event = new AuditEvent(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.MICROS), service,
                    eventType, traceId, spanId, actor, resource, outcome, durationMs, Map.copyOf(attributes));
            for (AuditSink sink : sinks) {
                try {
                    sink.record(event);
                } catch (RuntimeException e) {
                    log.error("Audit sink {} failed for event {} ({})", sink.getClass().getSimpleName(),
                            event.id(), event.eventType(), e);
                }
            }
        }
    }
}
