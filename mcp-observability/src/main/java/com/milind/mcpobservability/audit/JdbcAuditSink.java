package com.milind.mcpobservability.audit;

import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compliance sink: appends to {@code audit_event} (see {@code observability/postgres-init/001-audit.sql}),
 * chaining each row to the previous one by hash. Several services write to the same table, so each insert
 * takes a transaction-scoped advisory lock to keep the chain linear. UPDATE and DELETE are blocked by
 * triggers in the schema.
 */
public class JdbcAuditSink implements AuditSink {

    /** Arbitrary but fixed key shared by every writer. */
    private static final long CHAIN_LOCK = 7_340_001L;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JsonMapper mapper;

    public JdbcAuditSink(JdbcTemplate jdbc, TransactionTemplate tx, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.mapper = mapper;
    }

    @Override
    public void record(AuditEvent e) {
        String payload = mapper.writeValueAsString(e.attributes());
        tx.executeWithoutResult(status -> {
            jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {
            }, CHAIN_LOCK);
            List<String> last = jdbc.queryForList("SELECT hash FROM audit_event ORDER BY seq DESC LIMIT 1",
                    String.class);
            String prev = last.isEmpty() ? AuditChain.GENESIS : last.get(0);
            String hash = AuditChain.hash(prev, e.id().toString(), e.timestamp(), e.service(), e.eventType(),
                    e.traceId(), e.spanId(), e.actor(), e.resource(), e.outcome(), e.durationMs(), payload);
            jdbc.update("""
                    INSERT INTO audit_event (id, ts, service, event_type, trace_id, span_id, actor, resource,
                                             outcome, duration_ms, payload, prev_hash, hash)
                    VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, e.id().toString(), Timestamp.from(e.timestamp()), e.service(), e.eventType(), e.traceId(),
                    e.spanId(), e.actor(), e.resource(), e.outcome(), e.durationMs(), payload, prev, hash);
        });
    }

    /** Re-reads the whole table and checks the chain. For ops/audit use; O(n). */
    public AuditChain.Result verify() {
        List<AuditChain.Row> rows = jdbc.query("SELECT * FROM audit_event ORDER BY seq", (rs, i) ->
                new AuditChain.Row(rs.getLong("seq"), rs.getString("id"), rs.getTimestamp("ts").toInstant(),
                        rs.getString("service"), rs.getString("event_type"), rs.getString("trace_id"),
                        rs.getString("span_id"), rs.getString("actor"), rs.getString("resource"),
                        rs.getString("outcome"), rs.getLong("duration_ms"), rs.getString("payload"),
                        rs.getString("prev_hash"), rs.getString("hash")));
        Iterator<AuditChain.Row> it = rows.iterator();
        return AuditChain.verify(it);
    }
}
