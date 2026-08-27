package com.streamsense.apigateway.client;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.streamsense.apigateway.analytics.BrandSafetyMetrics;
import com.streamsense.apigateway.analytics.SponsorExposureMetric;
import com.streamsense.apigateway.analytics.StreamMetricBucket;
import com.streamsense.apigateway.analytics.StreamMetricsSummary;

import reactor.core.publisher.Mono;

@Component
public class AnalyticsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceClient.class);

    private static final ParameterizedTypeReference<List<StreamMetricBucket>> BUCKET_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<SponsorExposureMetric>> SPONSOR_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;

    public AnalyticsServiceClient(WebClient.Builder webClientBuilder,
            @Value("${streamsense.services.analytics-service.base-url:http://localhost:8085}") String baseUrl) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Mono<StreamMetricsSummary> summary(String streamer, String streamSessionId, int windowMinutes) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/streams/{streamer}/summary")
                            .queryParam("windowMinutes", windowMinutes);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(StreamMetricsSummary.class)
                .doOnSubscribe(subscription -> log.info("fetching analytics summary streamer={} windowMinutes={}",
                        streamer, windowMinutes));
    }

    public Mono<List<StreamMetricBucket>> timeseries(String streamer, String streamSessionId, int windowMinutes,
            int bucketSeconds) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/streams/{streamer}/timeseries")
                            .queryParam("windowMinutes", windowMinutes)
                            .queryParam("bucketSeconds", bucketSeconds);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(BUCKET_LIST)
                .doOnSubscribe(subscription -> log.info("fetching analytics timeseries streamer={} windowMinutes={} bucketSeconds={}",
                        streamer, windowMinutes, bucketSeconds));
    }

    public Mono<List<SponsorExposureMetric>> sponsorExposure(String streamer, String streamSessionId, int windowMinutes) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/streams/{streamer}/sponsors")
                            .queryParam("windowMinutes", windowMinutes);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(SPONSOR_LIST);
    }

    public Mono<BrandSafetyMetrics> risk(String streamer, String streamSessionId, int windowMinutes) {
        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/streams/{streamer}/risk")
                            .queryParam("windowMinutes", windowMinutes);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(BrandSafetyMetrics.class);
    }
}
