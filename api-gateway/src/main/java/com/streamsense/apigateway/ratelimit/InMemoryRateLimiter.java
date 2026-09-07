package com.streamsense.apigateway.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import reactor.core.publisher.Mono;

/**
 * Per-instance fixed-window counters. Correct for a single gateway; behind a load balancer every replica counts
 * separately, so the effective limit is multiplied by the replica count. Use {@link RedisRateLimiter} whenever
 * more than one gateway instance can serve a client.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastSweepEpochSecond = new AtomicLong();

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Mono<RateLimitDecision> acquire(String bucketId, int requestLimit, int windowSeconds) {
        return Mono.fromSupplier(() -> acquireNow(bucketId, requestLimit, windowSeconds));
    }

    RateLimitDecision acquireNow(String bucketId, int requestLimit, int windowSeconds) {
        long nowEpochSecond = clock.instant().getEpochSecond();
        long windowStart = (nowEpochSecond / windowSeconds) * windowSeconds;
        long resetAt = windowStart + windowSeconds;

        sweepExpired(nowEpochSecond, windowSeconds);

        WindowCounter counter = counters.compute(bucketId, (key, existing) -> {
            if (existing == null || existing.windowStartEpochSecond() != windowStart) {
                return new WindowCounter(windowStart, resetAt, 0);
            }
            return existing;
        });

        synchronized (counter) {
            if (counter.count() >= requestLimit) {
                return new RateLimitDecision(false, 0, resetAt);
            }
            counter.increment();
            return new RateLimitDecision(true, requestLimit - counter.count(), resetAt);
        }
    }

    int size() {
        return counters.size();
    }

    // Counters are only ever touched on the request path, so closed windows are reclaimed here instead of by a
    // background thread: at most one sweep per window, removing only entries whose window has already ended.
    private void sweepExpired(long nowEpochSecond, int windowSeconds) {
        long lastSweep = lastSweepEpochSecond.get();
        if (nowEpochSecond - lastSweep < windowSeconds) {
            return;
        }
        if (!lastSweepEpochSecond.compareAndSet(lastSweep, nowEpochSecond)) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAtEpochSecond() <= nowEpochSecond);
    }

    private static final class WindowCounter {
        private final long windowStartEpochSecond;
        private final long expiresAtEpochSecond;
        private int count;

        private WindowCounter(long windowStartEpochSecond, long expiresAtEpochSecond, int count) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.expiresAtEpochSecond = expiresAtEpochSecond;
            this.count = count;
        }

        private long windowStartEpochSecond() {
            return windowStartEpochSecond;
        }

        private long expiresAtEpochSecond() {
            return expiresAtEpochSecond;
        }

        private int count() {
            return count;
        }

        private void increment() {
            count += 1;
        }
    }
}
