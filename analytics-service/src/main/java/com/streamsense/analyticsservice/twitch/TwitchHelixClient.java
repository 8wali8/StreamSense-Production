package com.streamsense.analyticsservice.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/** The documented Twitch Helix API: which watched channels are live, with title, category, and viewers. */
public class TwitchHelixClient {

    /** Helix accepts up to 100 user_login values per request. */
    static final int MAX_LOGINS_PER_REQUEST = 100;

    private final RestClient restClient;
    private final TwitchAppTokenProvider tokens;
    private final StreamSenseProperties.Helix helix;

    public TwitchHelixClient(
            RestClient.Builder builder, TwitchAppTokenProvider tokens, StreamSenseProperties.Helix helix) {
        this.restClient = builder.baseUrl(helix.getBaseUrl()).build();
        this.tokens = tokens;
        this.helix = helix;
    }

    /** Every live stream among the given logins. Channels that are offline are simply absent. */
    public List<HelixStream> liveStreams(Collection<String> logins) {
        List<String> distinct = logins.stream().distinct().toList();
        List<HelixStream> streams = new ArrayList<>();
        for (int start = 0; start < distinct.size(); start += MAX_LOGINS_PER_REQUEST) {
            List<String> chunk = distinct.subList(start, Math.min(distinct.size(), start + MAX_LOGINS_PER_REQUEST));
            streams.addAll(parse(fetchStreams(chunk, true)));
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
            throw ex;
        } catch (ResourceAccessException ex) {
            // A slow name lookup or a dropped connection; one more try before giving up.
            if (retry) {
                return get(uri, false);
            }
            throw ex;
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

    private JsonNode fetchStreams(List<String> logins, boolean retryOnUnauthorized) {
        try {
            return restClient
                    .get()
                    .uri(uri -> streamsUri(uri, logins))
                    .header("Client-Id", helix.getClientId())
                    .header("Authorization", "Bearer " + tokens.token())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException ex) {
            if (retryOnUnauthorized && ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                tokens.invalidate();
                return fetchStreams(logins, false);
            }
            throw ex;
        }
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
