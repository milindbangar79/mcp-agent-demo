package com.milind.mcpobservability.audit;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * {@code GET /actuator/auditchain} re-reads the whole audit table and verifies the hash chain. Not exposed by
 * default; enable with {@code management.endpoints.web.exposure.include=auditchain} and protect it, since it
 * scans the table (O(n)).
 */
@Endpoint(id = "auditchain")
public class AuditChainEndpoint {

    private final JdbcAuditSink sink;

    public AuditChainEndpoint(JdbcAuditSink sink) {
        this.sink = sink;
    }

    @ReadOperation
    public AuditChain.Result verify() {
        return sink.verify();
    }
}
