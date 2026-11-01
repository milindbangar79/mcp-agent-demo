package com.milind.mcpobservability.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.common.McpTransportContext;

import org.springframework.ai.mcp.customizer.McpClientCustomizer;
import org.springframework.beans.factory.ObjectProvider;

/**
 * The MCP client uses the JDK {@code HttpClient}, which Spring does not instrument, so by default no trace
 * headers are sent and each MCP server starts a disconnected trace.
 *
 * <p>Reading {@code tracer.currentSpan()} inside the HTTP request customizer does not work: the SDK builds the
 * request on a Reactor thread that has no span. The SDK's {@code transportContextProvider} is evaluated on the
 * caller's thread though, so we capture the W3C {@code traceparent} there and hand it to
 * {@link TraceparentRequestCustomizer} through the {@link McpTransportContext}.
 */
public class TraceContextClientCustomizer implements McpClientCustomizer<McpClient.SyncSpec> {

    static final String KEY = "mcp.observability.trace-headers";

    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public TraceContextClientCustomizer(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void customize(String name, McpClient.SyncSpec spec) {
        spec.transportContextProvider(() -> {
            Tracer t = tracer.getIfAvailable();
            Propagator p = propagator.getIfAvailable();
            Span span = t == null ? null : t.currentSpan();
            if (span == null || p == null) {
                return McpTransportContext.EMPTY;
            }
            Map<String, Object> headers = new LinkedHashMap<>();
            p.inject(span.context(), headers, (carrier, key, value) -> carrier.put(key, value));
            return McpTransportContext.create(Map.of(KEY, headers));
        });
    }
}
