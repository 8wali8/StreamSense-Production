package com.streamsense.apigateway.client;

import com.streamsense.apigateway.config.DownstreamServicesProperties;
import com.streamsense.apigateway.model.Recommendation;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class RecommendationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceClient.class);
    private static final ParameterizedTypeReference<List<Recommendation>> RECOMMENDATION_LIST =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public RecommendationServiceClient(WebClient.Builder webClientBuilder, DownstreamServicesProperties services) {
        this.webClient = webClientBuilder
                .baseUrl(services.getRecommendationService().getBaseUrl())
                .build();
    }

    public Mono<List<Recommendation>> recommendations(String streamer, int limit) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/recommendations")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(RECOMMENDATION_LIST)
                .doOnSubscribe(
                        subscription -> log.info("fetching recommendations streamer={} limit={}", streamer, limit))
                .doOnSuccess(response -> log.info(
                        "received recommendations streamer={} count={}",
                        streamer,
                        response != null ? response.size() : 0));
    }
}
