package com.milind.employeemcp.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    SlidingWindowRateLimiter toolRateLimiter(
            @Value("${mcp.rate-limit.requests-per-second:100}") int requestsPerSecond) {
        return new SlidingWindowRateLimiter(requestsPerSecond);
    }
}
