package com.streamsense.apigateway.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.streamsense.apigateway.model.Recommendation;

import reactor.core.publisher.Mono;

@Component
public class RecommendationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceClient.class);
    private static final ParameterizedTypeReference<List<Recommendation>> RECOMMENDATION_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public RecommendationServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${streamsense.services.recommendation-service.base-url:http://localhost:8082}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Mono<List<Recommendation>> recommendations(String streamer, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/recommendations")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(RECOMMENDATION_LIST)
                .doOnSubscribe(subscription -> log.info("fetching recommendations streamer={} limit={}", streamer, limit))
                .doOnSuccess(response -> log.info("received recommendations streamer={} count={}", streamer,
                        response != null ? response.size() : 0));
    }
}
