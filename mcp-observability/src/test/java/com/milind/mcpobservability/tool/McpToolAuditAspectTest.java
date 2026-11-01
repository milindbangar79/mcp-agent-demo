package com.milind.mcpobservability.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import com.milind.mcpobservability.audit.AuditEvent;
import com.milind.mcpobservability.audit.AuditProperties;
import com.milind.mcpobservability.audit.AuditRecorder;
import com.milind.mcpratelimit.RateLimitExceededException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import tools.jackson.databind.json.JsonMapper;

class McpToolAuditAspectTest {

    public static class Tools {
        @McpTool(name = "echo", description = "d")
        public String echo(String text) {
            return "echo:" + text;
        }

        @McpTool(name = "limited", description = "d")
        public String limited() {
            throw new RateLimitExceededException("Rate limit exceeded");
        }

        @McpTool(name = "broken", description = "d")
        public String broken() {
            throw new IllegalArgumentException("bad input");
        }
    }

    private final List<AuditEvent> events = new ArrayList<>();
    private final MeterRegistry meters = new SimpleMeterRegistry();
    private Tools tools;

    @BeforeEach
    void setUp() {
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new DefaultMeterObservationHandler(meters));
        ObjectProvider<Tracer> noTracer = new DefaultListableBeanFactory().getBeanProvider(Tracer.class);
        var recorder = new AuditRecorder("employee-mcp", new AuditProperties(null, null, null, null),
                List.of(events::add), noTracer);
        var factory = new AspectJProxyFactory(new Tools());
        factory.setProxyTargetClass(true);
        factory.addAspect(new McpToolAuditAspect(registry, recorder, noTracer, JsonMapper.builder().build(),
                "employee-mcp"));
        tools = factory.getProxy();
    }

    private double timerCount(String outcome) {
        var t = meters.find("mcp.tool.call").tag("outcome", outcome).timer();
        return t == null ? 0 : t.count();
    }

    @Test
    void successIsAuditedWithArgumentsAndResult() {
        assertThat(tools.echo("hi")).isEqualTo("echo:hi");

        var e = events.get(0);
        assertThat(e.eventType()).isEqualTo("mcp.tool.invoke");
        assertThat(e.resource()).isEqualTo("echo");
        assertThat(e.outcome()).isEqualTo("success");
        assertThat((String) e.attributes().get("arguments")).contains("\"text\":\"hi\"");
        assertThat((String) e.attributes().get("result")).contains("echo:hi");
        assertThat(timerCount("success")).isEqualTo(1);
    }

    @Test
    void rateLimitRejectionIsOutcomeRateLimitedAndStillThrows() {
        assertThatThrownBy(() -> tools.limited()).isInstanceOf(RateLimitExceededException.class);

        assertThat(events.get(0).outcome()).isEqualTo("rate_limited");
        assertThat(timerCount("rate_limited")).isEqualTo(1);
    }

    @Test
    void otherFailuresAreOutcomeErrorWithTheExceptionType() {
        assertThatThrownBy(() -> tools.broken()).isInstanceOf(IllegalArgumentException.class);

        var e = events.get(0);
        assertThat(e.outcome()).isEqualTo("error");
        assertThat(e.attributes()).containsEntry("errorType", "IllegalArgumentException");
        assertThat(timerCount("error")).isEqualTo(1);
    }
}
