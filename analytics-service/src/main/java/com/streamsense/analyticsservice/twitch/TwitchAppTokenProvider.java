package com.streamsense.analyticsservice.twitch;

import com.fasterxml.jackson.databind.JsonNode;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import java.time.Clock;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * An app access token from the client-credentials grant, cached until shortly before it expires.
 * Helix accepts it together with the application's client id; no user login is involved.
 */
public class TwitchAppTokenProvider {

    private final RestClient restClient;
    private final StreamSenseProperties.Helix helix;
    private final Clock clock;

    private String token;
    private long expiresAt;

    public TwitchAppTokenProvider(RestClient.Builder builder, StreamSenseProperties.Helix helix, Clock clock) {
        this.restClient = builder.build();
        this.helix = helix;
        this.clock = clock;
    }

    public synchronized String token() {
        long now = clock.millis();
        if (token != null && now < expiresAt) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", helix.getClientId());
        form.add("client_secret", helix.getClientSecret());
        form.add("grant_type", "client_credentials");
        JsonNode body = restClient
                .post()
                .uri(helix.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        if (body == null || body.path("access_token").asText("").isBlank()) {
            throw new IllegalStateException("Twitch token response carried no access_token");
        }
        token = body.path("access_token").asText();
        long expiresInSeconds = body.path("expires_in").asLong(3600);
        expiresAt = now + Math.max(0, expiresInSeconds - helix.getTokenRefreshMarginSeconds()) * 1000L;
        return token;
    }

    /** Forget the cached token, for example after Helix answered 401. */
    public synchronized void invalidate() {
        token = null;
        expiresAt = 0;
    }
}
