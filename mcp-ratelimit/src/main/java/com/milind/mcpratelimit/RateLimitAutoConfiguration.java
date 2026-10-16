package com.milind.mcpratelimit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers ONE {@link ToolRateLimiter} per application, shared by all of its tools (server-wide
 * budget, enforced where each tool calls {@code acquireOrThrow()}). Define your own
 * {@code ToolRateLimiter} bean (e.g. one backed by a distributed Bucket4j ProxyManager) to override.
 */
@AutoConfiguration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ToolRateLimiter toolRateLimiter(RateLimitProperties properties) {
        return new ToolRateLimiter(properties.requestsPerSecond());
    }
}
