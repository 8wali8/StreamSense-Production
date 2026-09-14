package com.streamsense.analyticsservice.api;

/**
 * What the Helix poller last did, for the operations page. Counts only: the watched logins are
 * other channels' business and a streamer's token can read this route. {@code lastPollAt} is the
 * last poll that got an answer from Twitch; {@code lastAttemptAt} the last time the poller ran at
 * all; {@code pausedUntil} is set while a 429 keeps the client from asking; {@code lastError}
 * and {@code lastErrorAt} keep the most recent failure until a later poll succeeds. Times are
 * epoch milliseconds, null when the event has not happened.
 */
public record HelixPollStatus(
        boolean enabled,
        long pollIntervalMs,
        Long lastAttemptAt,
        Long lastPollAt,
        int watched,
        int live,
        int closed,
        Long pausedUntil,
        String lastError,
        Long lastErrorAt) {

    public static HelixPollStatus disabled() {
        return new HelixPollStatus(false, 0, null, null, 0, 0, 0, null, null, null);
    }

    /** Enabled, nothing has run yet. */
    public static HelixPollStatus idle(long pollIntervalMs) {
        return new HelixPollStatus(true, pollIntervalMs, null, null, 0, 0, 0, null, null, null);
    }

    public HelixPollStatus polled(long at, int watched, int live, int closed) {
        return new HelixPollStatus(true, pollIntervalMs, at, at, watched, live, closed, null, null, null);
    }

    public HelixPollStatus paused(long at, long until) {
        return new HelixPollStatus(
                true, pollIntervalMs, at, lastPollAt, watched, live, closed, until, lastError, lastErrorAt);
    }

    public HelixPollStatus failed(long at, String error) {
        return new HelixPollStatus(true, pollIntervalMs, at, lastPollAt, watched, live, closed, null, error, at);
    }
}
