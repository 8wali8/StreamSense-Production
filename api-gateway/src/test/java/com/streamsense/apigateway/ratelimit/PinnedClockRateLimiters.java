package com.streamsense.apigateway.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * An in-memory limiter whose clock never moves, for tests that count requests inside one window.
 *
 * <p>The windows are fixed and aligned to the wall clock ({@code (now / windowSeconds) * windowSeconds}), so a
 * test that sends two requests a millisecond apart lands them in different windows whenever it happens to
 * straddle a boundary, and the second one is counted as the first of a fresh window. Pinning the clock to the
 * start of a window removes that coin flip; it costs nothing, because no test here waits for a window to end.
 *
 * <p>Lives in this package because the clock-taking constructor is deliberately not public.
 */
public final class PinnedClockRateLimiters {

    private PinnedClockRateLimiters() {}

    /** A limiter pinned to the start of a window, so every request in a test falls inside the same one. */
    public static RateLimiter pinnedToWindowStart() {
        return new InMemoryRateLimiter(Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC));
    }
}
