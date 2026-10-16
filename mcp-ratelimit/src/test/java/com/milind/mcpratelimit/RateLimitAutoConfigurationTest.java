package com.milind.mcpratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RateLimitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimitAutoConfiguration.class));

    @Test
    void defaultsTo100PerSecondSharedSingleBean() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ToolRateLimiter.class);
            var limiter = ctx.getBean(ToolRateLimiter.class);
            for (int i = 0; i < 100; i++) {
                assertThat(limiter.tryAcquire()).isTrue();
            }
            assertThat(limiter.tryAcquire()).isFalse();
        });
    }

    @Test
    void honoursConfiguredLimit() {
        runner.withPropertyValues("mcp.rate-limit.requests-per-second=3").run(ctx -> {
            var limiter = ctx.getBean(ToolRateLimiter.class);
            assertThat(limiter.tryAcquire()).isTrue();
            assertThat(limiter.tryAcquire()).isTrue();
            assertThat(limiter.tryAcquire()).isTrue();
            assertThat(limiter.tryAcquire()).isFalse();
        });
    }

    @Test
    void invalidLimitFailsStartup() {
        runner.withPropertyValues("mcp.rate-limit.requests-per-second=0")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void userDefinedBeanWins() {
        var custom = new ToolRateLimiter(1);
        runner.withBean(ToolRateLimiter.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(ToolRateLimiter.class)).isSameAs(custom));
    }
}
