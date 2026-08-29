package com.streamsense.apigateway.ratelimit;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * Fixed-window counters shared by every gateway instance through Redis. One key per bucket and window; the
 * increment and the expiry are set in a single Lua call, so two replicas racing on the first request of a window
 * cannot leave a key without a TTL. Keys expire one second after their window closes.
 *
 * <p>When Redis cannot be reached the limiter either lets the request through (fail-open, the default: an edge
 * limiter outage should not take the site down) or rejects it (fail-closed, for when the downstream must be
 * protected at all costs). Either way the outage is counted in
 * {@code streamsense_gateway_rate_limit_store_errors_total} and logged.
 */
public class RedisRateLimiter implements RateLimiter {

    static final String KEY_PREFIX = "streamsense:ratelimit:";

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final RedisScript<Long> INCREMENT_WITH_EXPIRY = RedisScript.of(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """,
            Long.class);

    private final ReactiveStringRedisTemplate redis;
    private final Clock clock;
    private final boolean failOpen;
    private final Counter storeErrors;

    public RedisRateLimiter(ReactiveStringRedisTemplate redis, Clock clock, boolean failOpen, MeterRegistry meterRegistry) {
        this.redis = redis;
        this.clock = clock;
        this.failOpen = failOpen;
        this.storeErrors = meterRegistry.counter("streamsense_gateway_rate_limit_store_errors_total", "store", "redis");
    }

    @Override
    public Mono<RateLimitDecision> acquire(String bucketId, int requestLimit, int windowSeconds) {
        long nowEpochSecond = clock.instant().getEpochSecond();
        long windowStart = (nowEpochSecond / windowSeconds) * windowSeconds;
        long resetAt = windowStart + windowSeconds;
        String key = KEY_PREFIX + bucketId + ":" + windowStart;

        return redis.execute(INCREMENT_WITH_EXPIRY, List.of(key), List.of(String.valueOf(windowSeconds + 1)))
                .next()
                .map(count -> decide(count, requestLimit, resetAt))
                .onErrorResume(error -> {
                    storeErrors.increment();
                    log.warn("rate limit store unavailable bucket={} failOpen={} error={}", bucketId, failOpen, error.toString());
                    return Mono.just(failOpen
                            ? new RateLimitDecision(true, requestLimit, resetAt)
                            : new RateLimitDecision(false, 0, resetAt));
                });
    }

    private static RateLimitDecision decide(long count, int requestLimit, long resetAt) {
        if (count > requestLimit) {
            return new RateLimitDecision(false, 0, resetAt);
        }
        return new RateLimitDecision(true, (int) (requestLimit - count), resetAt);
    }
}
