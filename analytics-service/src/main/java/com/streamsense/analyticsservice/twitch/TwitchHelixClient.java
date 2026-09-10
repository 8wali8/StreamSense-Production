package com.streamsense.analyticsservice.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * The documented Twitch Helix API: which watched channels are live, with title, category, and viewers.
 *
 * <p>Helix budgets requests per application (800 points a minute, observed in the {@code Ratelimit-*}
 * headers). When it answers 429 the client stops sending until the bucket refills, as told by
 * {@code Ratelimit-Reset} (epoch seconds) or {@code Retry-After}, falling back to the configured
 * back-off; every call in between fails fast with {@link HelixRateLimitedException} so the poller
 * and the import panel wait instead of spending the next minute's budget on retries.
 */
public class TwitchHelixClient {

    private static final Logger log = LoggerFactory.getLogger(TwitchHelixClient.class);

    /** Helix accepts up to 100 user_login values per request. */
    static final int MAX_LOGINS_PER_REQUEST = 100;

    /** A reset header further away than this is treated as bogus and replaced by the configured back-off. */
    static final long MAX_PAUSE_MS = 5 * 60_000L;

    private final RestClient restClient;
    private final TwitchAppTokenProvider tokens;
    private final StreamSenseProperties.Helix helix;
    private final Clock clock;
    private final AtomicLong pausedUntil = new AtomicLong();

    public TwitchHelixClient(
            RestClient.Builder builder, TwitchAppTokenProvider tokens, StreamSenseProperties.Helix helix) {
        this(builder, tokens, helix, Clock.systemUTC());
    }

    public TwitchHelixClient(
            RestClient.Builder builder, TwitchAppTokenProvider tokens, StreamSenseProperties.Helix helix, Clock clock) {
        this.restClient = builder.baseUrl(helix.getBaseUrl()).build();
        this.tokens = tokens;
        this.helix = helix;
        this.clock = clock;
    }

    /** Epoch milliseconds until which requests are refused after a 429; zero when not paused. */
    long pausedUntil() {
        return pausedUntil.get();
    }

    /** Every live stream among the given logins. Channels that are offline are simply absent. */
    public List<HelixStream> liveStreams(Collection<String> logins) {
        List<String> distinct = logins.stream().distinct().toList();
        List<HelixStream> streams = new ArrayList<>();
        for (int start = 0; start < distinct.size(); start += MAX_LOGINS_PER_REQUEST) {
            List<String> chunk = distinct.subList(start, Math.min(distinct.size(), start + MAX_LOGINS_PER_REQUEST));
            streams.addAll(parse(get(uri -> streamsUri(uri, chunk))));
        }
        return streams;
    }

    /** The channel's recordings of past broadcasts, newest first. Empty when the login is unknown. */
    public List<HelixVideo> archives(String login, int limit) {
        String userId = userId(login);
        if (userId == null) {
            return List.of();
        }
        int first = Math.max(1, Math.min(100, limit));
        JsonNode body = get(uri -> uri.path("/videos")
                .queryParam("user_id", userId)
                .queryParam("type", "archive")
                .queryParam("first", first)
                .build());
        return parseVideos(body);
    }

    /** One recording by video id. */
    public Optional<HelixVideo> video(String vodId) {
        JsonNode body = get(uri -> uri.path("/videos").queryParam("id", vodId).build());
        return parseVideos(body).stream().findFirst();
    }

    private String userId(String login) {
        JsonNode body = get(uri -> uri.path("/users").queryParam("login", login).build());
        for (JsonNode node : body == null ? List.<JsonNode>of() : body.path("data")) {
            String id = node.path("id").asText("");
            if (!id.isBlank()) {
                return id;
            }
        }
        return null;
    }

    private JsonNode get(java.util.function.Function<UriBuilder, java.net.URI> uri) {
        return get(uri, true);
    }

    private JsonNode get(java.util.function.Function<UriBuilder, java.net.URI> uri, boolean retry) {
        long until = pausedUntil.get();
        if (clock.millis() < until) {
            throw new HelixRateLimitedException(until);
        }
        try {
            return restClient
                    .get()
                    .uri(uri)
                    .header("Client-Id", helix.getClientId())
                    .header("Authorization", "Bearer " + tokens.token())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException ex) {
            if (retry && ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokens.invalidate();
                return get(uri, false);
            }
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw pause(ex);
            }
            throw ex;
        } catch (ResourceAccessException ex) {
            // A slow name lookup or a dropped connection; one more try before giving up.
            if (retry) {
                return get(uri, false);
            }
            throw ex;
        }
    }

    /**
     * Record a 429. The clock is read here, after the answer arrived, so a relative {@code Retry-After}
     * counts from Twitch's answer rather than from when the request (and maybe a token fetch) began.
     * The deadline only ever moves later: the poller and import requests share this client, and a
     * late-arriving 429 with an earlier reset must not cut short a pause another answer set.
     */
    HelixRateLimitedException pause(HttpClientErrorException ex) {
        long until = pausedUntil.accumulateAndGet(resetAt(ex, clock.millis()), Math::max);
        log.warn("helix answered 429; pausing requests until {}", Instant.ofEpochMilli(until));
        return new HelixRateLimitedException(until);
    }

    /**
     * When to resume: Twitch's {@code Ratelimit-Reset} (epoch seconds, the documented header) or
     * {@code Retry-After} (seconds), whichever is present and sane, else the configured back-off.
     */
    long resetAt(HttpClientErrorException ex, long now) {
        // Bounds are checked in seconds before any conversion: an absurd but parseable header value
        // times 1000 would overflow to a negative that slips under the cap.
        long maxPauseSeconds = MAX_PAUSE_MS / 1000;
        String reset =
                ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Ratelimit-Reset");
        Long resetAt = parseLong(reset);
        if (resetAt != null) {
            long aheadSeconds = resetAt - now / 1000;
            if (aheadSeconds > 0 && aheadSeconds <= maxPauseSeconds) {
                return resetAt * 1000L;
            }
        }
        String retryAfter =
                ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
        Long seconds = parseLong(retryAfter);
        if (seconds != null && seconds > 0 && seconds <= maxPauseSeconds) {
            return now + seconds * 1000L;
        }
        return now + Math.min(MAX_PAUSE_MS, Math.max(0, helix.getRateLimitBackoffMs()));
    }

    private static Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static List<HelixVideo> parseVideos(JsonNode body) {
        List<HelixVideo> videos = new ArrayList<>();
        if (body == null) {
            return videos;
        }
        for (JsonNode node : body.path("data")) {
            String id = node.path("id").asText("");
            if (id.isBlank()) {
                continue;
            }
            String streamId = node.path("stream_id").asText("");
            videos.add(new HelixVideo(
                    id,
                    streamId.isBlank() ? null : streamId,
                    node.path("user_login").asText("").toLowerCase(java.util.Locale.ROOT),
                    node.path("title").asText(null),
                    parseInstant(node.path("created_at").asText("")),
                    parseDuration(node.path("duration").asText("")),
                    node.path("url").asText(null),
                    node.path("view_count").asLong(0)));
        }
        return videos;
    }

    private static final Pattern DURATION = Pattern.compile("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    /** Helix durations read like "3h2m1s"; any part may be absent. */
    static long parseDuration(String value) {
        Matcher matcher = DURATION.matcher(value == null ? "" : value.trim());
        if (!matcher.matches()) {
            return 0;
        }
        long hours = matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1));
        long minutes = matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2));
        long seconds = matcher.group(3) == null ? 0 : Long.parseLong(matcher.group(3));
        return ((hours * 60 + minutes) * 60 + seconds) * 1000L;
    }

    private java.net.URI streamsUri(UriBuilder uri, List<String> logins) {
        uri.path("/streams").queryParam("first", MAX_LOGINS_PER_REQUEST);
        for (String login : logins) {
            uri.queryParam("user_login", login);
        }
        return uri.build();
    }

    static List<HelixStream> parse(JsonNode body) {
        List<HelixStream> streams = new ArrayList<>();
        if (body == null) {
            return streams;
        }
        for (JsonNode node : body.path("data")) {
            String id = node.path("id").asText("");
            String login = node.path("user_login").asText("");
            if (id.isBlank() || login.isBlank()) {
                continue;
            }
            streams.add(new HelixStream(
                    id,
                    login,
                    node.path("title").asText(null),
                    node.path("game_name").asText(null),
                    node.path("viewer_count").asInt(0),
                    parseInstant(node.path("started_at").asText(""))));
        }
        return streams;
    }

    private static long parseInstant(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (DateTimeParseException ex) {
            return System.currentTimeMillis();
        }
    }
}
