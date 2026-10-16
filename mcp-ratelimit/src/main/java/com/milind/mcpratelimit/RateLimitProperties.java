package com.milind.mcpratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code mcp.rate-limit.requests-per-second}: size of the single token bucket shared by every
 * tool on a server. Burst capacity equals this value.
 */
@ConfigurationProperties(prefix = "mcp.rate-limit")
public record RateLimitProperties(Integer requestsPerSecond) {

    public static final int DEFAULT_REQUESTS_PER_SECOND = 100;

    public RateLimitProperties {
        if (requestsPerSecond == null) {
            requestsPerSecond = DEFAULT_REQUESTS_PER_SECOND;
        }
        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("mcp.rate-limit.requests-per-second must be positive");
        }
    }
}
