package com.milind.mcpobservability;

import java.util.List;

import javax.sql.DataSource;

import com.milind.mcpobservability.audit.AuditChainEndpoint;
import com.milind.mcpobservability.audit.AuditProperties;
import com.milind.mcpobservability.audit.AuditRecorder;
import com.milind.mcpobservability.audit.AuditSink;
import com.milind.mcpobservability.audit.JdbcAuditSink;
import com.milind.mcpobservability.audit.LogAuditSink;
import com.milind.mcpobservability.llm.LlmAuditObservationHandlers;
import com.milind.mcpobservability.mcp.TraceContextClientCustomizer;
import com.milind.mcpobservability.mcp.TraceparentRequestCustomizer;
import com.milind.mcpobservability.tool.McpToolAuditAspect;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the audit trail and the capture points. Each capture point is guarded by {@code @ConditionalOnClass}
 * so one library serves MCP servers (aspect), agents (LLM handlers, MCP client propagation) or both.
 */
@AutoConfiguration
@EnableConfigurationProperties(AuditProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JsonMapper auditJsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "mcp.audit.log", name = "enabled", havingValue = "true", matchIfMissing = true)
    AuditSink logAuditSink(JsonMapper auditJsonMapper) {
        return new LogAuditSink(auditJsonMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mcp.audit.jdbc", name = "enabled", havingValue = "true")
    JdbcAuditSink jdbcAuditSink(AuditProperties props, JsonMapper auditJsonMapper) {
        var jdbc = props.jdbc();
        var cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbc.url());
        cfg.setUsername(jdbc.username());
        cfg.setPassword(jdbc.password());
        cfg.setMaximumPoolSize(2);
        cfg.setPoolName("audit-pool");
        DataSource ds = new HikariDataSource(cfg);
        return new JdbcAuditSink(new JdbcTemplate(ds),
                new TransactionTemplate(new DataSourceTransactionManager(ds)), auditJsonMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mcp.audit.jdbc", name = "enabled", havingValue = "true")
    AuditChainEndpoint auditChainEndpoint(JdbcAuditSink jdbcAuditSink) {
        return new AuditChainEndpoint(jdbcAuditSink);
    }

    @Bean
    AuditRecorder auditRecorder(@Value("${spring.application.name:unknown}") String service,
                                AuditProperties props, List<AuditSink> sinks, ObjectProvider<Tracer> tracer) {
        return new AuditRecorder(service, props, sinks, tracer);
    }

    /** Attaches the Logback OTel appender (declared in logback-spring.xml) to the SDK so logs are exported over OTLP. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender")
    static class OtelLogExport {

        @Bean
        org.springframework.beans.factory.InitializingBean otelLogAppenderInstaller(
                ObjectProvider<io.opentelemetry.api.OpenTelemetry> openTelemetry) {
            return () -> {
                var otel = openTelemetry.getIfAvailable();
                if (otel != null) {
                    io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender.install(otel);
                }
            };
        }
    }

    /** MCP server side: instrument every {@code @McpTool}. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"org.springframework.ai.mcp.annotation.McpTool", "org.aspectj.lang.ProceedingJoinPoint"})
    static class McpServerCapture {

        @Bean
        McpToolAuditAspect mcpToolAuditAspect(ObservationRegistry registry, AuditRecorder audit,
                                              ObjectProvider<Tracer> tracer, JsonMapper auditJsonMapper,
                                              @Value("${spring.application.name:unknown}") String server) {
            return new McpToolAuditAspect(registry, audit, tracer, auditJsonMapper, server);
        }
    }

    /** Agent side: audit every model round-trip and every tool the model calls. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.ai.chat.observation.ChatModelObservationContext")
    static class LlmCapture {

        @Bean
        LlmAuditObservationHandlers.Chat chatAuditHandler(AuditRecorder audit, JsonMapper auditJsonMapper) {
            return new LlmAuditObservationHandlers.Chat(audit, auditJsonMapper);
        }

        @Bean
        LlmAuditObservationHandlers.Tool toolAuditHandler(AuditRecorder audit) {
            return new LlmAuditObservationHandlers.Tool(audit);
        }
    }

    /** Agent side: carry the trace into MCP servers. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer")
    static class McpClientPropagation {

        @Bean
        TraceparentRequestCustomizer traceparentRequestCustomizer() {
            return new TraceparentRequestCustomizer();
        }

        @Bean
        TraceContextClientCustomizer traceContextClientCustomizer(ObjectProvider<Tracer> tracer,
                                                                  ObjectProvider<Propagator> propagator) {
            return new TraceContextClientCustomizer(tracer, propagator);
        }
    }
}
