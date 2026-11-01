package com.milind.mcpobservability.audit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Audit trail settings ({@code mcp.audit.*}).
 *
 * @param content         how prompts, completions, tool arguments and tool results are stored
 *                        (never in traces, only in the audit sinks)
 * @param maxContentChars per-field cap before truncation; {@code truncated} is flagged on the event
 * @param log             console/OTLP-log sink (developer debugging, Loki)
 * @param jdbc            append-only, hash-chained Postgres sink (compliance)
 */
@ConfigurationProperties(prefix = "mcp.audit")
public record AuditProperties(Content content, Integer maxContentChars, Log log, Jdbc jdbc) {

    public enum Content {
        /** Store the text itself (dev default). The audit sinks then hold PII. */
        FULL,
        /** Store only SHA-256 and length; the text is never written. */
        REDACTED
    }

    public record Log(Boolean enabled) {
    }

    public record Jdbc(Boolean enabled, String url, String username, String password) {
    }

    public AuditProperties {
        content = content == null ? Content.FULL : content;
        maxContentChars = maxContentChars == null ? 20_000 : maxContentChars;
        if (maxContentChars < 100) {
            throw new IllegalArgumentException("mcp.audit.max-content-chars must be >= 100");
        }
        log = log == null ? new Log(true) : new Log(log.enabled() == null || log.enabled());
        jdbc = jdbc == null ? new Jdbc(false, null, null, null)
                : new Jdbc(Boolean.TRUE.equals(jdbc.enabled()), jdbc.url(), jdbc.username(), jdbc.password());
    }
}
