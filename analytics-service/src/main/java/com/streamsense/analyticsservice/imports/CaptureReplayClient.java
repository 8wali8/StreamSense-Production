package com.streamsense.analyticsservice.imports;

import java.util.HashMap;
import java.util.Map;
import org.springframework.web.client.RestClient;

/** Asks video-capture-service to sample a recording's frames and audio with the original timestamps. */
public class CaptureReplayClient {

    private final RestClient restClient;

    public CaptureReplayClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public void replay(
            String channel, String vodId, String vodUrl, long baseTimeMs, long durationMs, String streamSessionId) {
        Map<String, Object> body = new HashMap<>();
        body.put("channel", channel);
        body.put("vodId", vodId);
        body.put("vodUrl", vodUrl);
        body.put("baseTimeMs", baseTimeMs);
        body.put("durationSeconds", durationMs / 1000);
        body.put("streamSessionId", streamSessionId);
        restClient.post().uri("/api/video/replay").body(body).retrieve().toBodilessEntity();
    }
}
