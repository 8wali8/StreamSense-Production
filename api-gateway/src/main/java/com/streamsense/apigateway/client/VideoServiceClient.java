package com.streamsense.apigateway.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.streamsense.apigateway.events.SponsorDetectionEvent;

import reactor.core.publisher.Mono;

@Component
public class VideoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceClient.class);

    private static final ParameterizedTypeReference<List<SponsorDetectionEvent>> SPONSOR_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public VideoServiceClient(WebClient.Builder webClientBuilder,
            @Value("${streamsense.services.video-service.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Mono<List<SponsorDetectionEvent>> recentDetections(String streamer, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/video/detections/recent")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(SPONSOR_LIST)
                .doOnSubscribe(subscription -> log.info("fetching recent sponsor detections streamer={} limit={}",
                        streamer, limit))
                .doOnSuccess(response -> log.info("received recent sponsor detections streamer={} count={}", streamer,
                        response != null ? response.size() : 0));
    }
}
