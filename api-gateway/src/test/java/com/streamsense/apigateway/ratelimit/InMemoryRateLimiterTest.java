package com.streamsense.apigateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    @Test
    void allowsRequestsWithinWindowAndRejectsOverflow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-11T12:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        assertThat(limiter.acquire("chat:127.0.0.1", 2, 60).allowed()).isTrue();
        assertThat(limiter.acquire("chat:127.0.0.1", 2, 60).allowed()).isTrue();
        assertThat(limiter.acquire("chat:127.0.0.1", 2, 60).allowed()).isFalse();
    }

    @Test
    void resetsCountsInNextWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-04-11T12:00:00Z"));
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        limiter.acquire("chat:127.0.0.1", 1, 60);
        assertThat(limiter.acquire("chat:127.0.0.1", 1, 60).allowed()).isFalse();

        clock.setInstant(Instant.parse("2026-04-11T12:01:00Z"));

        assertThat(limiter.acquire("chat:127.0.0.1", 1, 60).allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
