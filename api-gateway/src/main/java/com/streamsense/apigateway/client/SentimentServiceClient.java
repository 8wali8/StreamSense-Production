package com.streamsense.apigateway.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.events.TranscriptSegmentEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;

import reactor.core.publisher.Mono;

@Component
public class SentimentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SentimentServiceClient.class);

    private static final ParameterizedTypeReference<List<SentimentAnalysisEvent>> SENTIMENT_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<TranscriptSegmentEvent>> TRANSCRIPT_SEGMENT_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<TranscriptSentimentEvent>> TRANSCRIPT_SENTIMENT_LIST =
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

    public Mono<List<SentimentAnalysisEvent>> recentSponsorSentiment(String streamer, String sponsor, int limit) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/sentiment/sponsor/recent")
                            .queryParam("streamer", streamer)
                            .queryParam("limit", limit);
                    if (sponsor != null && !sponsor.isBlank()) {
                        builder.queryParam("sponsor", sponsor);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(SENTIMENT_LIST)
                .doOnSubscribe(subscription -> log.info("fetching recent sponsor sentiment streamer={} sponsor={} limit={}",
                        streamer, sponsor, limit))
                .doOnSuccess(response -> log.info("received recent sponsor sentiment streamer={} sponsor={} count={}",
                        streamer, sponsor, response != null ? response.size() : 0));
    }

    public Mono<List<TranscriptSegmentEvent>> recentTranscriptSegments(String streamer, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/sentiment/transcript/recent")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(TRANSCRIPT_SEGMENT_LIST)
                .doOnSubscribe(subscription -> log.info("fetching recent transcript streamer={} limit={}", streamer, limit))
                .doOnSuccess(response -> log.info("received recent transcript streamer={} count={}", streamer,
                        response != null ? response.size() : 0));
    }

    public Mono<List<TranscriptSentimentEvent>> recentTranscriptSentiment(String streamer, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/api/sentiment/transcript/sentiment/recent")
                        .queryParam("streamer", streamer)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .bodyToMono(TRANSCRIPT_SENTIMENT_LIST)
                .doOnSubscribe(subscription -> log.info("fetching recent transcript sentiment streamer={} limit={}", streamer, limit))
                .doOnSuccess(response -> log.info("received recent transcript sentiment streamer={} count={}", streamer,
                        response != null ? response.size() : 0));
    }

    public Mono<List<TranscriptSentimentEvent>> recentSponsorTranscriptSentiment(String streamer, String sponsor, int limit) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/sentiment/transcript/sponsor/recent")
                            .queryParam("streamer", streamer)
                            .queryParam("limit", limit);
                    if (sponsor != null && !sponsor.isBlank()) {
                        builder.queryParam("sponsor", sponsor);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(TRANSCRIPT_SENTIMENT_LIST)
                .doOnSubscribe(subscription -> log.info(
                        "fetching recent sponsor transcript sentiment streamer={} sponsor={} limit={}",
                        streamer, sponsor, limit))
                .doOnSuccess(response -> log.info(
                        "received recent sponsor transcript sentiment streamer={} sponsor={} count={}",
                        streamer, sponsor, response != null ? response.size() : 0));
    }
}
