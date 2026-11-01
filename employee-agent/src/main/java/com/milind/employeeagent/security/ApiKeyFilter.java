package com.milind.employeeagent.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.milind.mcpobservability.audit.AuditRecorder;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects every /api/** call that lacks the shared secret in the X-API-Key header. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);

    private final byte[] expected;
    private final ObjectProvider<AuditRecorder> audit;
    private final ObjectProvider<MeterRegistry> meters;

    /** Plain constructor (no telemetry), used by unit tests. */
    public ApiKeyFilter(String apiKey) {
        this(apiKey, null, null);
    }

    @Autowired
    public ApiKeyFilter(@Value("${agent.security.api-key}") String apiKey,
                        ObjectProvider<AuditRecorder> audit, ObjectProvider<MeterRegistry> meters) {
        this.audit = audit;
        this.meters = meters;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("agent.security.api-key must be configured");
        }
        this.expected = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // Constant-time comparison to avoid leaking the key through timing.
        boolean authorised = provided != null
                && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
        record(request, authorised);
        if (!authorised) {
            log.warn("Rejected request to {}: missing or invalid {}", request.getRequestURI(), HEADER);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"missing or invalid " + HEADER + "\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Every agent-to-agent request is audited (accepted and rejected) and counted; failures here never block the request. */
    private void record(HttpServletRequest request, boolean authorised) {
        String outcome = authorised ? "accepted" : "rejected";
        try {
            if (meters != null && meters.getIfAvailable() != null) {
                meters.getIfAvailable().counter("agent.auth", "outcome", outcome).increment();
            }
            if (audit != null && audit.getIfAvailable() != null) {
                audit.getIfAvailable().event("agent.request")
                        .actor(authorised ? "shared-key-client" : "unauthenticated")
                        .resource(request.getMethod() + " " + request.getRequestURI())
                        .outcome(outcome)
                        .meta("remoteAddr", request.getRemoteAddr())
                        .commit();
            }
        } catch (RuntimeException e) {
            log.error("Failed to record agent request telemetry", e);
        }
    }
}
