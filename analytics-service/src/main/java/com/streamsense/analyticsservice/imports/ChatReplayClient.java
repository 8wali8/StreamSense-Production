package com.streamsense.analyticsservice.imports;

import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/** Asks chat-service to publish a recording's chat with the original timestamps, and to report on or stop it. */
public class ChatReplayClient {

    private final RestClient restClient;

    public ChatReplayClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Starts the replay; {@code startOffsetSeconds} above zero resumes from there. */
    public void replay(String channel, String vodId, long baseTimeMs, String streamSessionId, long startOffsetSeconds) {
        restClient
                .post()
                .uri("/api/chat/replay")
                .body(Map.of(
                        "channel", channel,
                        "vodId", vodId,
                        "baseTimeMs", baseTimeMs,
                        "streamSessionId", streamSessionId,
                        "startOffsetSeconds", startOffsetSeconds))
                .retrieve()
                .toBodilessEntity();
    }

    /** Empty when chat-service knows nothing about the recording (never started there, or restarted since). */
    public Optional<ReplayStatus> status(String vodId) {
        return ReplayCalls.status(restClient, "/api/chat/replay/" + vodId);
    }

    /** Stops the replay of one recording; empty when chat-service knows nothing about it. */
    public Optional<ReplayStatus> stop(String vodId) {
        return ReplayCalls.stop(restClient, "/api/chat/replay/" + vodId);
    }
}
