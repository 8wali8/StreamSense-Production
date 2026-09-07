package com.streamsense.recommendationservice.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.streamsense.recommendationservice.config.StreamSenseProperties;

@Component
public class SponsorHistoryClient {

    private static final Logger log = LoggerFactory.getLogger(SponsorHistoryClient.class);
    private static final ParameterizedTypeReference<List<SponsorSignal>> SPONSOR_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public SponsorHistoryClient(RestClient.Builder restClientBuilder, StreamSenseProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getServices().getVideoService().getBaseUrl())
                .build();
    }

    public List<SponsorSignal> recentDetections(String streamer, int limit) {
        log.info("fetching recent sponsor detections streamer={} limit={}", streamer, limit);
        List<SponsorSignal> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/video/detections/recent")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(SPONSOR_LIST);

        return response != null ? response : List.of();
    }
}
