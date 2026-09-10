package com.streamsense.analyticsservice.imports;

import java.util.Map;
import org.springframework.web.client.RestClient;

/** Asks chat-service to publish a recording's chat with the original timestamps. */
public class ChatReplayClient {

    private final RestClient restClient;

    public ChatReplayClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public void replay(String channel, String vodId, long baseTimeMs, String streamSessionId) {
        restClient
                .post()
                .uri("/api/chat/replay")
                .body(Map.of(
                        "channel", channel,
                        "vodId", vodId,
                        "baseTimeMs", baseTimeMs,
                        "streamSessionId", streamSessionId))
                .retrieve()
                .toBodilessEntity();
    }
}
