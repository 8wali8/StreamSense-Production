package com.streamsense.apigateway.client;

import com.streamsense.apigateway.analytics.AnalyticsRange;
import com.streamsense.apigateway.analytics.BrandSafetyMetrics;
import com.streamsense.apigateway.analytics.Deal;
import com.streamsense.apigateway.analytics.DealSummary;
import com.streamsense.apigateway.analytics.SessionSummary;
import com.streamsense.apigateway.analytics.SponsorExposureMetric;
import com.streamsense.apigateway.analytics.StreamMetricBucket;
import com.streamsense.apigateway.analytics.StreamMetricsSummary;
import com.streamsense.apigateway.analytics.StreamSession;
import com.streamsense.apigateway.config.DownstreamServicesProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class AnalyticsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsServiceClient.class);

    private static final ParameterizedTypeReference<List<StreamMetricBucket>> BUCKET_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<SponsorExposureMetric>> SPONSOR_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<StreamSession>> SESSION_LIST =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<Deal>> DEAL_LIST = new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    public AnalyticsServiceClient(WebClient.Builder webClientBuilder, DownstreamServicesProperties services) {
        this.webClient = webClientBuilder
                .baseUrl(services.getAnalyticsService().getBaseUrl())
                .build();
    }

    public Mono<StreamMetricsSummary> summary(String streamer, String streamSessionId, int windowMinutes) {
        return summary(streamer, streamSessionId, windowMinutes, AnalyticsRange.NONE);
    }

    public Mono<StreamMetricsSummary> summary(
            String streamer, String streamSessionId, Integer windowMinutes, AnalyticsRange range) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/analytics/streams/{streamer}/summary")
                            .queryParamIfPresent("windowMinutes", java.util.Optional.ofNullable(windowMinutes));
                    range.apply(builder);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(StreamMetricsSummary.class)
                .doOnSubscribe(subscription ->
                        log.info("fetching analytics summary streamer={} windowMinutes={}", streamer, windowMinutes));
    }

    public Mono<List<StreamMetricBucket>> timeseries(
            String streamer, String streamSessionId, int windowMinutes, int bucketSeconds) {
        return timeseries(streamer, streamSessionId, windowMinutes, bucketSeconds, AnalyticsRange.NONE);
    }

    public Mono<List<StreamMetricBucket>> timeseries(
            String streamer, String streamSessionId, Integer windowMinutes, int bucketSeconds, AnalyticsRange range) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/analytics/streams/{streamer}/timeseries")
                            .queryParamIfPresent("windowMinutes", java.util.Optional.ofNullable(windowMinutes))
                            .queryParam("bucketSeconds", bucketSeconds);
                    range.apply(builder);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(BUCKET_LIST)
                .doOnSubscribe(subscription -> log.info(
                        "fetching analytics timeseries streamer={} windowMinutes={} bucketSeconds={}",
                        streamer,
                        windowMinutes,
                        bucketSeconds));
    }

    public Mono<List<SponsorExposureMetric>> sponsorExposure(
            String streamer, String streamSessionId, int windowMinutes) {
        return sponsorExposure(streamer, streamSessionId, windowMinutes, AnalyticsRange.NONE);
    }

    public Mono<List<SponsorExposureMetric>> sponsorExposure(
            String streamer, String streamSessionId, Integer windowMinutes, AnalyticsRange range) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/analytics/streams/{streamer}/sponsors")
                            .queryParamIfPresent("windowMinutes", java.util.Optional.ofNullable(windowMinutes));
                    range.apply(builder);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(SPONSOR_LIST);
    }

    public Mono<BrandSafetyMetrics> risk(String streamer, String streamSessionId, int windowMinutes) {
        return risk(streamer, streamSessionId, windowMinutes, AnalyticsRange.NONE);
    }

    public Mono<BrandSafetyMetrics> risk(
            String streamer, String streamSessionId, Integer windowMinutes, AnalyticsRange range) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/analytics/streams/{streamer}/risk")
                            .queryParamIfPresent("windowMinutes", java.util.Optional.ofNullable(windowMinutes));
                    range.apply(builder);
                    if (streamSessionId != null && !streamSessionId.isBlank()) {
                        builder.queryParam("streamSessionId", streamSessionId);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(BrandSafetyMetrics.class);
    }

    public Mono<List<StreamSession>> sessions(String streamer, Long from, Long to, Integer limit) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/streams/{streamer}/sessions");
                    if (from != null) {
                        builder.queryParam("from", from);
                    }
                    if (to != null) {
                        builder.queryParam("to", to);
                    }
                    if (limit != null) {
                        builder.queryParam("limit", limit);
                    }
                    return builder.build(streamer);
                })
                .retrieve()
                .bodyToMono(SESSION_LIST);
    }

    /** Empty when the session does not exist, so the GraphQL field resolves to null instead of an error. */
    public Mono<StreamSession> session(long id) {
        return webClient
                .get()
                .uri("/api/analytics/sessions/{id}", id)
                .retrieve()
                .bodyToMono(StreamSession.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    public Mono<SessionSummary> sessionSummary(
            long id, String sponsor, String chatCommand, String trackedLinkHost, Double cpm, Double hostReadRate) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/sessions/{id}/summary");
                    if (sponsor != null && !sponsor.isBlank()) {
                        builder.queryParam("sponsor", sponsor);
                    }
                    if (chatCommand != null && !chatCommand.isBlank()) {
                        builder.queryParam("chatCommand", chatCommand);
                    }
                    if (trackedLinkHost != null && !trackedLinkHost.isBlank()) {
                        builder.queryParam("trackedLinkHost", trackedLinkHost);
                    }
                    if (cpm != null) {
                        builder.queryParam("cpmPer30sEquivalent", cpm);
                    }
                    if (hostReadRate != null) {
                        builder.queryParam("hostReadRatePer1000", hostReadRate);
                    }
                    return builder.build(id);
                })
                .retrieve()
                .bodyToMono(SessionSummary.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    /** A streamer's deals, or every deal when no streamer is given. */
    public Mono<List<Deal>> deals(String streamer, Integer limit) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/analytics/deals");
                    if (streamer != null && !streamer.isBlank()) {
                        builder.queryParam("streamer", streamer);
                    }
                    if (limit != null) {
                        builder.queryParam("limit", limit);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(DEAL_LIST);
    }

    /** Empty when the deal does not exist. */
    public Mono<Deal> deal(long id) {
        return webClient
                .get()
                .uri("/api/analytics/deals/{id}", id)
                .retrieve()
                .bodyToMono(Deal.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    /** The deal a share token opens; empty when the token is unknown or revoked. */
    public Mono<Deal> shareDeal(String token) {
        return webClient
                .get()
                .uri("/api/analytics/share/{token}", token)
                .retrieve()
                .bodyToMono(Deal.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    /** Empty when the deal does not exist. */
    public Mono<DealSummary> dealSummary(long id) {
        return webClient
                .get()
                .uri("/api/analytics/deals/{id}/summary", id)
                .retrieve()
                .bodyToMono(DealSummary.class)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty());
    }

    /** Buckets over an absolute range, for the report timeline. */
    public Mono<List<StreamMetricBucket>> timeseriesInRange(String streamer, long from, long to, int bucketSeconds) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/analytics/streams/{streamer}/timeseries")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("bucketSeconds", bucketSeconds)
                        .build(streamer))
                .retrieve()
                .bodyToMono(BUCKET_LIST);
    }
}
