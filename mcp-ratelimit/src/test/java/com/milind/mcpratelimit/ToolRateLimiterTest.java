package com.milind.mcpratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicLong;

import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.Test;

class ToolRateLimiterTest {

    /** Manually advanced clock so tests are deterministic. */
    private static class FakeClock implements TimeMeter {
        final AtomicLong nanos = new AtomicLong();
        @Override public long currentTimeNanos() { return nanos.get(); }
        @Override public boolean isWallClockBased() { return false; }
    }

    @Test
    void allowsBurstUpToLimitThenRejects() {
        var limiter = new ToolRateLimiter(100, new FakeClock());
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();
        assertThatThrownBy(limiter::acquireOrThrow).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void refillsOverTime() {
        var clock = new FakeClock();
        var limiter = new ToolRateLimiter(100, clock);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire();
        }
        clock.nanos.addAndGet(1_000_000_000L);   // one full second -> bucket full again
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire()).isTrue();
        }
        assertThat(limiter.tryAcquire()).isFalse();
    }

    @Test
    void partialRefillIsProportional() {
        var clock = new FakeClock();
        var limiter = new ToolRateLimiter(100, clock);
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire();
        }
        clock.nanos.addAndGet(100_000_000L);     // 100 ms -> ~10 tokens
        int admitted = 0;
        while (limiter.tryAcquire()) {
            admitted++;
        }
        assertThat(admitted).isBetween(9, 11);
    }
}
