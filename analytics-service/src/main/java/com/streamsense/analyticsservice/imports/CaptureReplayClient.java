package com.streamsense.analyticsservice.imports;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;

/**
 * Asks video-capture-service to sample a recording's frames and audio with the original timestamps,
 * and to report on or stop that import.
 */
public class CaptureReplayClient {

    private final RestClient restClient;

    public CaptureReplayClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Starts the replay; {@code startOffsetSeconds} above zero resumes from there. */
    public void replay(
            String channel,
            String vodId,
            String vodUrl,
            long baseTimeMs,
            long durationMs,
            String streamSessionId,
            long startOffsetSeconds) {
        Map<String, Object> body = new HashMap<>();
        body.put("channel", channel);
        body.put("vodId", vodId);
        body.put("vodUrl", vodUrl);
        body.put("baseTimeMs", baseTimeMs);
        body.put("durationSeconds", durationMs / 1000);
        body.put("streamSessionId", streamSessionId);
        body.put("startOffsetSeconds", startOffsetSeconds);
        restClient.post().uri("/api/video/capture/replay").body(body).retrieve().toBodilessEntity();
    }

    /** Empty when the capture service knows nothing about the recording (never started there, or restarted since). */
    public Optional<ReplayStatus> status(String vodId) {
        return ReplayCalls.status(restClient, "/api/video/capture/replay/" + vodId);
    }

    /** Stops the replay of one recording; empty when the capture service knows nothing about it. */
    public Optional<ReplayStatus> stop(String vodId) {
        return ReplayCalls.stop(restClient, "/api/video/capture/replay/" + vodId);
    }
}
