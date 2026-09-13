package com.streamsense.analyticsservice.twitch;

import java.time.Instant;

/**
 * Twitch answered 429 (the application's Helix budget is spent), or an earlier 429 has paused the
 * client and the pause has not yet ended. No request was sent in the second case.
 */
public class HelixRateLimitedException extends RuntimeException {

    private final long retryAtMillis;

    public HelixRateLimitedException(long retryAtMillis) {
        super("Twitch is rate limiting this application; retry after " + Instant.ofEpochMilli(retryAtMillis));
        this.retryAtMillis = retryAtMillis;
    }

    /** Epoch milliseconds at which the client sends requests again. */
    public long retryAtMillis() {
        return retryAtMillis;
    }

    /** Whole seconds until then, at least one, for a {@code Retry-After} header. */
    public long retryAfterSeconds(long nowMillis) {
        return Math.max(1, (retryAtMillis - nowMillis + 999) / 1000);
    }
}
