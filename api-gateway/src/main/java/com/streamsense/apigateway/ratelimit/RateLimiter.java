package com.streamsense.apigateway.ratelimit;

import reactor.core.publisher.Mono;

/**
 * Fixed-window request counting keyed by rule and client. Implementations must be safe to call from the
 * WebFlux request path, so a store round-trip is expressed as a {@link Mono} rather than a blocking call.
 */
public interface RateLimiter {

    /**
     * Counts one request against {@code bucketId} in the window that contains "now".
     *
     * @param bucketId      rule id plus client key, for example {@code chat-ingest:203.0.113.10}
     * @param requestLimit  requests allowed per window
     * @param windowSeconds window length; windows are aligned to multiples of it since the epoch
     */
    Mono<RateLimitDecision> acquire(String bucketId, int requestLimit, int windowSeconds);

    record RateLimitDecision(boolean allowed, int remaining, long resetAtEpochSeconds) {
    }
}
