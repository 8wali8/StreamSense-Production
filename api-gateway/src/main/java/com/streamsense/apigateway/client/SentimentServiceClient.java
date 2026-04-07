package com.streamsense.apigateway.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.streamsense.apigateway.events.SentimentAnalysisEvent;

import reactor.core.publisher.Mono;

@Component
public class SentimentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SentimentServiceClient.class);

    private static final ParameterizedTypeReference<List<SentimentAnalysisEvent>> SENTIMENT_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public SentimentServiceClient(WebClient.Builder webClientBuilder,
            @Value("${streamsense.services.sentiment-service.base-url}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Mono<List<SentimentAnalysisEvent>> recentSentiment(String streamer, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/sentiment/recent")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(SENTIMENT_LIST)
                .doOnSubscribe(subscription -> log.info("fetching recent sentiment streamer={} limit={}", streamer, limit))
                .doOnSuccess(response -> log.info("received recent sentiment streamer={} count={}", streamer,
                        response != null ? response.size() : 0));
    }
}
