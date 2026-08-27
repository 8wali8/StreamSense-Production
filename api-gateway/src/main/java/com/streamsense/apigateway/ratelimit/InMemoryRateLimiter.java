package com.streamsense.apigateway.ratelimit;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimiter {

    private final Clock clock;
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public InMemoryRateLimiter() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public RateLimitDecision acquire(String bucketId, int requestLimit, int windowSeconds) {
        long nowEpochSecond = clock.instant().getEpochSecond();
        long windowStart = (nowEpochSecond / windowSeconds) * windowSeconds;
        long resetAt = windowStart + windowSeconds;

        WindowCounter counter = counters.compute(bucketId, (key, existing) -> {
            if (existing == null || existing.windowStartEpochSecond() != windowStart) {
                return new WindowCounter(windowStart, 0);
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

    public record RateLimitDecision(boolean allowed, int remaining, long resetAtEpochSeconds) {
    }

    private static final class WindowCounter {

        private final long windowStartEpochSecond;
        private int count;

        private WindowCounter(long windowStartEpochSecond, int count) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.count = count;
        }

        private long windowStartEpochSecond() {
            return windowStartEpochSecond;
        }

        private int count() {
            return count;
        }

        private void increment() {
            count += 1;
        }
    }
}
