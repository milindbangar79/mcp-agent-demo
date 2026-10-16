package com.milind.employeemcp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SlidingWindowRateLimiterTest {

    @Test
    void admitsExactlyLimitPerSecondThenRejects() {
        var clock = new AtomicLong();
        var limiter = new SlidingWindowRateLimiter(100, clock::get);
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();
        assertThatThrownBy(limiter::acquireOrThrow).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void budgetRecoversAfterWindowSlides() {
        var clock = new AtomicLong();
        var limiter = new SlidingWindowRateLimiter(100, clock::get);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire();
        }
        clock.addAndGet(1_000_000_000L);
        assertThat(limiter.tryAcquire()).isTrue();
    }
}
