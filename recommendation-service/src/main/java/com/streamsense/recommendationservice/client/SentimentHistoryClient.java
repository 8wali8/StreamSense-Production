package com.streamsense.recommendationservice.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.streamsense.recommendationservice.config.StreamSenseProperties;

@Component
public class SentimentHistoryClient {

    private static final Logger log = LoggerFactory.getLogger(SentimentHistoryClient.class);
    private static final ParameterizedTypeReference<List<SentimentSignal>> SENTIMENT_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public SentimentHistoryClient(RestClient.Builder restClientBuilder, StreamSenseProperties properties) {
        this.restClient = restClientBuilder
                .baseUrl(properties.getServices().getSentimentService().getBaseUrl() != null
                        ? properties.getServices().getSentimentService().getBaseUrl()
                        : "http://localhost:8083")
                .build();
    }

    public List<SentimentSignal> recentSentiment(String streamer, int limit) {
        log.info("fetching recent sentiment history streamer={} limit={}", streamer, limit);
        List<SentimentSignal> response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/sentiment/recent")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(SENTIMENT_LIST);

        return response != null ? response : List.of();
    }
}
