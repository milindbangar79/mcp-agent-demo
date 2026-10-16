package com.milind.employeemcp.ratelimit;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/**
 * Strict sliding-window limiter: never admits more than {@code maxPerSecond} calls in any
 * trailing one-second window. One instance is shared by every tool on the server.
 */
public class SlidingWindowRateLimiter {

    private static final long WINDOW_NANOS = 1_000_000_000L;

    private final int maxPerSecond;
    private final LongSupplier nanoClock;
    private final ArrayDeque<Long> admitted = new ArrayDeque<>();

    public SlidingWindowRateLimiter(int maxPerSecond) {
        this(maxPerSecond, System::nanoTime);
    }

    public SlidingWindowRateLimiter(int maxPerSecond, LongSupplier nanoClock) {
        if (maxPerSecond <= 0) {
            throw new IllegalArgumentException("maxPerSecond must be positive");
        }
        this.maxPerSecond = maxPerSecond;
        this.nanoClock = nanoClock;
    }

    public synchronized boolean tryAcquire() {
        long now = nanoClock.getAsLong();
        while (!admitted.isEmpty() && now - admitted.peekFirst() >= WINDOW_NANOS) {
            admitted.pollFirst();
        }
        if (admitted.size() >= maxPerSecond) {
            return false;
        }
        admitted.addLast(now);
        return true;
    }

    /** Throws if the shared budget is exhausted; surfaced to the MCP client as a tool error. */
    public void acquireOrThrow() {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded: max " + maxPerSecond + " tool calls per second. Retry shortly.");
        }
    }
}
