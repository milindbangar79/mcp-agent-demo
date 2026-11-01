package com.milind.mcpobservability.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class AuditRecorderTest {

    private final List<AuditEvent> captured = new ArrayList<>();
    private final AuditSink sink = captured::add;

    private AuditRecorder recorder(AuditProperties.Content content, int max, AuditSink... sinks) {
        ObjectProvider<Tracer> noTracer = new DefaultListableBeanFactory().getBeanProvider(Tracer.class);
        List<AuditSink> all = new ArrayList<>(List.of(sinks));
        return new AuditRecorder("svc", new AuditProperties(content, max, null, null), all, noTracer);
    }

    @Test
    void fullPolicyStoresTextAndMetaAsIs() {
        recorder(AuditProperties.Content.FULL, 1000, sink).event("llm.call").resource("m")
                .meta("inputTokens", 12).content("prompt", "hello").commit();

        var attrs = captured.get(0).attributes();
        assertThat(attrs).containsEntry("prompt", "hello").containsEntry("inputTokens", 12);
        assertThat(captured.get(0).actor()).isEqualTo("anonymous");
    }

    @Test
    void fullPolicyTruncatesOversizedContentAndFlagsIt() {
        recorder(AuditProperties.Content.FULL, 100, sink).event("x").content("result", "a".repeat(250)).commit();

        var attrs = captured.get(0).attributes();
        assertThat((String) attrs.get("result")).hasSize(100);
        assertThat(attrs).containsEntry("resultTruncated", true).containsEntry("resultChars", 250);
    }

    @Test
    void redactedPolicyNeverWritesTheText() {
        recorder(AuditProperties.Content.REDACTED, 1000, sink).event("x").meta("model", "llama")
                .content("prompt", "salary of E001").commit();

        var attrs = captured.get(0).attributes();
        assertThat(attrs).doesNotContainKey("prompt").containsEntry("model", "llama");
        assertThat(attrs.get("promptSha256")).isEqualTo(AuditRecorder.sha256("salary of E001"));
        assertThat(attrs).containsEntry("promptChars", 14);
        assertThat(attrs.toString()).doesNotContain("salary of E001");
    }

    @Test
    void failingSinkDoesNotBreakTheCallerOrOtherSinks() {
        AuditSink broken = e -> {
            throw new IllegalStateException("db down");
        };
        recorder(AuditProperties.Content.FULL, 1000, broken, sink).event("x").commit();

        assertThat(captured).hasSize(1);
    }

    @Test
    void timestampsSurviveAPostgresRoundTrip() {
        recorder(AuditProperties.Content.FULL, 1000, sink).event("x").commit();

        var ts = captured.get(0).timestamp();
        assertThat(ts.getNano() % 1000).isZero();
    }
}
