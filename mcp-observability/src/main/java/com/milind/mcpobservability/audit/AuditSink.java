package com.milind.mcpobservability.audit;

/** Destination for audit events. Implementations must not throw into the business flow. */
public interface AuditSink {

    void record(AuditEvent event);
}
