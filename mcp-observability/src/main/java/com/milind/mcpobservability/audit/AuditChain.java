package com.milind.mcpobservability.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;

/**
 * The hash chain behind the Postgres sink: {@code hash = SHA-256(prevHash, event fields, payload)}, each row
 * committing to the one before it. Editing, deleting or re-ordering any row breaks every hash after it.
 * (Truncating the tail is only detectable against an externally anchored latest hash.)
 */
public final class AuditChain {

    public static final String GENESIS = "0".repeat(64);

    private static final char SEP = '\u001f';

    /** A stored row, exactly as read back from the database. */
    public record Row(long seq, String id, Instant timestamp, String service, String eventType, String traceId,
                      String spanId, String actor, String resource, String outcome, long durationMs,
                      String payload, String prevHash, String hash) {
    }

    /** {@code firstBrokenSeq} is {@code null} when the chain is intact. */
    public record Result(boolean valid, long checkedRows, Long firstBrokenSeq, String reason) {
    }

    private AuditChain() {
    }

    public static String hash(String prevHash, String id, Instant timestamp, String service, String eventType,
                              String traceId, String spanId, String actor, String resource, String outcome,
                              long durationMs, String payload) {
        var sb = new StringBuilder(256 + (payload == null ? 0 : payload.length()));
        for (String part : new String[] {prevHash, id, timestamp.toString(), service, eventType, traceId, spanId,
                actor, resource, outcome, Long.toString(durationMs), payload}) {
            sb.append(part == null ? "" : part).append(SEP);
        }
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rows must be supplied in {@code seq} order. */
    public static Result verify(Iterator<Row> rows) {
        String expectedPrev = GENESIS;
        long checked = 0;
        while (rows.hasNext()) {
            Row r = rows.next();
            checked++;
            if (!expectedPrev.equals(r.prevHash())) {
                return new Result(false, checked, r.seq(), "prev_hash does not match the previous row's hash");
            }
            String recomputed = hash(r.prevHash(), r.id(), r.timestamp(), r.service(), r.eventType(), r.traceId(),
                    r.spanId(), r.actor(), r.resource(), r.outcome(), r.durationMs(), r.payload());
            if (!recomputed.equals(r.hash())) {
                return new Result(false, checked, r.seq(), "row content does not match its hash");
            }
            expectedPrev = r.hash();
        }
        return new Result(true, checked, null, null);
    }
}
