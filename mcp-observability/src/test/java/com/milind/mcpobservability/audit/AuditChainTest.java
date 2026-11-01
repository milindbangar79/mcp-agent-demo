package com.milind.mcpobservability.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuditChainTest {

    private static final Instant T = Instant.parse("2026-11-01T10:00:00.123456Z");

    private static List<AuditChain.Row> chain(int n) {
        List<AuditChain.Row> rows = new ArrayList<>();
        String prev = AuditChain.GENESIS;
        for (int i = 1; i <= n; i++) {
            String payload = "{\"i\":" + i + "}";
            String hash = AuditChain.hash(prev, "id-" + i, T, "svc", "llm.call", "trace", "span", "anonymous",
                    "model", "success", 5, payload);
            rows.add(new AuditChain.Row(i, "id-" + i, T, "svc", "llm.call", "trace", "span", "anonymous", "model",
                    "success", 5, payload, prev, hash));
            prev = hash;
        }
        return rows;
    }

    @Test
    void intactChainVerifies() {
        var result = AuditChain.verify(chain(5).iterator());
        assertThat(result.valid()).isTrue();
        assertThat(result.checkedRows()).isEqualTo(5);
    }

    @Test
    void editedRowIsDetectedAtThatRow() {
        var rows = chain(5);
        var r = rows.get(2);
        rows.set(2, new AuditChain.Row(r.seq(), r.id(), r.timestamp(), r.service(), r.eventType(), r.traceId(),
                r.spanId(), r.actor(), r.resource(), "error", r.durationMs(), r.payload(), r.prevHash(), r.hash()));
        var result = AuditChain.verify(rows.iterator());
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(3);
    }

    @Test
    void deletedRowBreaksTheChainAtTheNextRow() {
        var rows = chain(5);
        rows.remove(1);
        var result = AuditChain.verify(rows.iterator());
        assertThat(result.valid()).isFalse();
        assertThat(result.firstBrokenSeq()).isEqualTo(3);
    }

    @Test
    void emptyChainIsValid() {
        assertThat(AuditChain.verify(List.<AuditChain.Row>of().iterator()).valid()).isTrue();
    }
}
