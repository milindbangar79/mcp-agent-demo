package com.milind.mcpratelimit;

import java.time.Duration;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.TimeMeter;

/**
 * One shared token bucket for every tool on this server: capacity = refill = {@code maxPerSecond}.
 * Bursts up to the capacity are allowed; the long-run average is capped at {@code maxPerSecond}/s.
 *
 * <p>The bucket is local to this JVM. For multiple instances, build the {@link Bucket} from a
 * Bucket4j {@code ProxyManager} (Redis/JCache/Hazelcast) instead — callers do not change.
 */
public class ToolRateLimiter {

    private final Bucket bucket;
    private final int maxPerSecond;

    public ToolRateLimiter(int maxPerSecond) {
        this(maxPerSecond, TimeMeter.SYSTEM_NANOTIME);
    }

    public ToolRateLimiter(int maxPerSecond, TimeMeter timeMeter) {
        if (maxPerSecond <= 0) {
            throw new IllegalArgumentException("maxPerSecond must be positive");
        }
        this.maxPerSecond = maxPerSecond;
        this.bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(maxPerSecond).refillGreedy(maxPerSecond, Duration.ofSeconds(1)))
                .withCustomTimePrecision(timeMeter)
                .build();
    }

    public boolean tryAcquire() {
        return bucket.tryConsume(1);
    }

    /** Throws if the shared budget is exhausted; surfaced to the MCP client as a tool error. */
    public void acquireOrThrow() {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded: max " + maxPerSecond + " tool calls per second. Retry shortly.");
        }
    }
}
