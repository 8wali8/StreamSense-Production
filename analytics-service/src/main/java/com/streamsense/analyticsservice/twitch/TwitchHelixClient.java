package com.streamsense.analyticsservice.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
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
