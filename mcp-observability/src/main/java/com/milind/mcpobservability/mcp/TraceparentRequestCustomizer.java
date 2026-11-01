package com.milind.mcpobservability.mcp;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Map;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import io.modelcontextprotocol.common.McpTransportContext;

/**
 * Second half of MCP trace propagation: copies the headers that {@link TraceContextClientCustomizer} captured
 * on the calling thread (see there for why) onto the outgoing HTTP request.
 */
public class TraceparentRequestCustomizer implements McpSyncHttpClientRequestCustomizer {

    @Override
    public void customize(HttpRequest.Builder builder, String method, URI endpoint, String body,
                          McpTransportContext context) {
        if (context != null && context.get(TraceContextClientCustomizer.KEY) instanceof Map<?, ?> headers) {
            headers.forEach((k, v) -> builder.header(String.valueOf(k), String.valueOf(v)));
        }
    }
}
