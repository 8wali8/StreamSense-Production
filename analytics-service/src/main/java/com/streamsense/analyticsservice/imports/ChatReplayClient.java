package com.streamsense.analyticsservice.imports;

import com.streamsense.analyticsservice.model.ChatLogLine;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Asks chat-service to publish a recording's chat, from the lines a streamer supplied, with the
 * original timestamps; and to report on or stop that replay.
 */
public class ChatReplayClient {

    private final RestClient restClient;

    public ChatReplayClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /**
     * Starts the replay of {@code lines}; {@code startOffsetSeconds} above zero resumes from there, and the
     * lines before it are left out here so the request carries only what is still to publish.
     */
    public void replay(
            String channel,
            String vodId,
            long baseTimeMs,
            String streamSessionId,
            long startOffsetSeconds,
            List<ChatLogLine> lines) {
        List<Map<String, Object>> comments = lines.stream()
                .filter(line -> line.offsetSeconds() >= startOffsetSeconds)
                .map(line -> Map.<String, Object>of(
                        "offsetSeconds", line.offsetSeconds(), "user", line.user(), "message", line.message()))
                .toList();
        restClient
                .post()
                .uri("/api/chat/replay")
                .body(Map.of(
                        "channel", channel,
                        "vodId", vodId,
                        "baseTimeMs", baseTimeMs,
                        "streamSessionId", streamSessionId,
                        "startOffsetSeconds", startOffsetSeconds,
                        "comments", comments))
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
