package com.streamsense.analyticsservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.config.TwitchHelixConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Token handling and response parsing against a local stand-in for id.twitch.tv and api.twitch.tv. */
class TwitchHelixClientTest {

    @org.junit.jupiter.api.Test
    void parsesArchivesWithTheirDurations() {
        com.fasterxml.jackson.databind.JsonNode body;
        try {
            body = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(
                            """
                            {"data": [{"id": "2750461300", "stream_id": "41", "user_login": "Racer", "title": "Monza",
                                       "created_at": "2026-09-01T18:00:00Z", "duration": "3h2m1s",
                                       "url": "https://www.twitch.tv/videos/2750461300", "view_count": 1200},
                                      {"id": "", "duration": "1h"}]}
                            """);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
        java.util.List<HelixVideo> videos = TwitchHelixClient.parseVideos(body);
        org.assertj.core.api.Assertions.assertThat(videos).hasSize(1);
        HelixVideo video = videos.get(0);
        org.assertj.core.api.Assertions.assertThat(video.userLogin()).isEqualTo("racer");
        org.assertj.core.api.Assertions.assertThat(video.streamId()).isEqualTo("41");
        org.assertj.core.api.Assertions.assertThat(video.durationMs()).isEqualTo(((3 * 60 + 2) * 60 + 1) * 1000L);
        org.assertj.core.api.Assertions.assertThat(video.createdAt())
                .isEqualTo(java.time.Instant.parse("2026-09-01T18:00:00Z").toEpochMilli());
        org.assertj.core.api.Assertions.assertThat(TwitchHelixClient.parseDuration("45m"))
                .isEqualTo(2_700_000L);
        org.assertj.core.api.Assertions.assertThat(TwitchHelixClient.parseDuration("garbage"))
                .isZero();
    }

    private HttpServer server;
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final List<String> authorizations = new ArrayList<>();
    private boolean rejectFirstStreamsCall;
    private String rateLimitResetHeader;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            int n = tokenRequests.incrementAndGet();
            respond(
                    exchange,
                    200,
                    "{\"access_token\":\"token-" + n + "\",\"expires_in\":3600,\"token_type\":\"bearer\"}");
        });
        server.createContext("/helix/streams", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (rejectFirstStreamsCall) {
                rejectFirstStreamsCall = false;
                respond(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            if (rateLimitResetHeader != null) {
                exchange.getResponseHeaders().add("Ratelimit-Reset", rateLimitResetHeader);
                exchange.getResponseHeaders().add("Ratelimit-Remaining", "0");
                respond(exchange, 429, "{\"error\":\"Too Many Requests\"}");
                return;
            }
            respond(
                    exchange,
                    200,
                    """
                    {"data":[{"id":"41","user_login":"racer","user_name":"Racer","game_name":"Formula 1",
                              "title":"Monza night","viewer_count":1342,"started_at":"2026-09-07T21:27:00Z"},
                             {"id":"","user_login":"broken"}]}
                    """);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void fetchesOneTokenParsesStreamsAndRefreshesAfterUnauthorized() {
        StreamSenseProperties.Helix helix = new StreamSenseProperties.Helix();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        helix.setClientId("client");
        helix.setClientSecret("secret");
        helix.setBaseUrl(base + "/helix");
        helix.setTokenUrl(base + "/oauth2/token");
        TwitchAppTokenProvider tokens = new TwitchAppTokenProvider(RestClient.builder(), helix, Clock.systemUTC());
        TwitchHelixClient client = new TwitchHelixClient(RestClient.builder(), tokens, helix);

        List<HelixStream> first = client.liveStreams(List.of("racer", "racer", "other"));
        List<HelixStream> second = client.liveStreams(List.of("racer"));

        assertThat(first).hasSize(1);
        assertThat(first.get(0))
                .isEqualTo(new HelixStream("41", "racer", "Monza night", "Formula 1", 1342, 1788816420000L));
        assertThat(second).hasSize(1);
        // The token is fetched once and reused; every Helix call carries it as a bearer.
        assertThat(tokenRequests.get()).isEqualTo(1);
        assertThat(authorizations).containsExactly("Bearer token-1", "Bearer token-1");

        rejectFirstStreamsCall = true;
        assertThat(client.liveStreams(List.of("racer"))).hasSize(1);
        assertThat(tokenRequests.get()).isEqualTo(2);
        assertThat(authorizations).endsWith("Bearer token-1", "Bearer token-2");
    }

    @Test
    void aTooManyRequestsAnswerPausesEveryCallUntilTheResetTime() {
        StreamSenseProperties.Helix helix = new StreamSenseProperties.Helix();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        helix.setClientId("client");
        helix.setClientSecret("secret");
        helix.setBaseUrl(base + "/helix");
        helix.setTokenUrl(base + "/oauth2/token");
        MutableClock clock = new MutableClock(1_800_000_000_000L);
        TwitchAppTokenProvider tokens = new TwitchAppTokenProvider(RestClient.builder(), helix, clock);
        // The production request factory: without it Apache HttpClient would quietly resend the 429 itself.
        RestClient.Builder builder = RestClient.builder().requestFactory(TwitchHelixConfig.twitchRequestFactory(helix));
        TwitchHelixClient client = new TwitchHelixClient(builder, tokens, helix, clock);
        long resetSeconds = 1_800_000_000L + 30;
        rateLimitResetHeader = Long.toString(resetSeconds);

        assertThatThrownBy(() -> client.liveStreams(List.of("racer")))
                .isInstanceOf(HelixRateLimitedException.class)
                .satisfies(ex -> assertThat(((HelixRateLimitedException) ex).retryAtMillis())
                        .isEqualTo(resetSeconds * 1000L));
        assertThat(client.pausedUntil()).isEqualTo(resetSeconds * 1000L);
        rateLimitResetHeader = null;

        // Still paused: no request reaches Twitch, whichever endpoint is asked.
        assertThatThrownBy(() -> client.liveStreams(List.of("racer"))).isInstanceOf(HelixRateLimitedException.class);
        assertThatThrownBy(() -> client.archives("racer", 5)).isInstanceOf(HelixRateLimitedException.class);
        assertThat(authorizations).hasSize(1);

        clock.advanceMillis(31_000L);
        assertThat(client.liveStreams(List.of("racer"))).hasSize(1);
        assertThat(authorizations).hasSize(2);
    }

    @Test
    void resetTimeComesFromTheHeadersOrTheConfiguredBackoff() {
        StreamSenseProperties.Helix helix = new StreamSenseProperties.Helix();
        helix.setRateLimitBackoffMs(45_000L);
        MutableClock clock = new MutableClock(1_800_000_000_000L);
        TwitchHelixClient client = new TwitchHelixClient(
                RestClient.builder(), new TwitchAppTokenProvider(RestClient.builder(), helix, clock), helix, clock);
        long now = clock.millis();

        assertThat(client.resetAt(tooMany(null, null), now)).isEqualTo(now + 45_000L);
        assertThat(client.resetAt(tooMany("1800000020", null), now)).isEqualTo(now + 20_000L);
        assertThat(client.resetAt(tooMany(null, "7"), now)).isEqualTo(now + 7_000L);
        // A reset in the past, an hour away, or unparsable falls back rather than stalling or spinning.
        assertThat(client.resetAt(tooMany("1799999990", null), now)).isEqualTo(now + 45_000L);
        assertThat(client.resetAt(tooMany("1800003600", null), now)).isEqualTo(now + 45_000L);
        assertThat(client.resetAt(tooMany("soon", "later"), now)).isEqualTo(now + 45_000L);
        // Values that would overflow when multiplied by 1000 are out of range, not negative pauses.
        String huge = Long.toString(Long.MAX_VALUE / 500);
        assertThat(client.resetAt(tooMany(huge, null), now)).isEqualTo(now + 45_000L);
        assertThat(client.resetAt(tooMany(null, huge), now)).isEqualTo(now + 45_000L);
        assertThat(client.resetAt(tooMany(Long.toString(Long.MIN_VALUE), Long.toString(Long.MIN_VALUE)), now))
                .isEqualTo(now + 45_000L);
        assertThat(new HelixRateLimitedException(now + 1_500L).retryAfterSeconds(now))
                .isEqualTo(2);
        assertThat(new HelixRateLimitedException(now - 1L).retryAfterSeconds(now))
                .isEqualTo(1);
    }

    @Test
    void thePauseCountsFromTheAnswerAndOnlyEverMovesLater() {
        StreamSenseProperties.Helix helix = new StreamSenseProperties.Helix();
        helix.setRateLimitBackoffMs(45_000L);
        MutableClock clock = new MutableClock(1_800_000_000_000L);
        TwitchHelixClient client = new TwitchHelixClient(
                RestClient.builder(), new TwitchAppTokenProvider(RestClient.builder(), helix, clock), helix, clock);

        // A relative Retry-After is measured from when the 429 is read, not from before the request.
        long start = clock.millis();
        clock.advanceMillis(4_000L);
        assertThat(client.pause(tooMany(null, "10")).retryAtMillis()).isEqualTo(start + 14_000L);

        // The poller and an import request share the client: a later 429 with an earlier reset (or a
        // shorter fallback) leaves the longer pause in place; a later reset extends it.
        assertThat(client.pause(tooMany(null, "3")).retryAtMillis()).isEqualTo(start + 14_000L);
        assertThat(client.pause(tooMany(null, null)).retryAtMillis()).isEqualTo(start + 4_000L + 45_000L);
        assertThat(client.pausedUntil()).isEqualTo(start + 4_000L + 45_000L);
    }

    private static org.springframework.web.client.HttpClientErrorException tooMany(String reset, String retryAfter) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (reset != null) {
            headers.add("Ratelimit-Reset", reset);
        }
        if (retryAfter != null) {
            headers.add("Retry-After", retryAfter);
        }
        return org.springframework.web.client.HttpClientErrorException.create(
                org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                headers,
                new byte[0],
                StandardCharsets.UTF_8);
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long millis) {
            this.millis = millis;
        }

        void advanceMillis(long delta) {
            millis += delta;
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return java.time.Instant.ofEpochMilli(millis);
        }

        @Override
        public long millis() {
            return millis;
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
