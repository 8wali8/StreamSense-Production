package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.BrandSafetyMetrics;
import com.streamsense.apigateway.analytics.SponsorExposureMetric;
import com.streamsense.apigateway.analytics.StreamMetricBucket;
import com.streamsense.apigateway.analytics.StreamMetricsSummary;
import com.streamsense.apigateway.client.AnalyticsServiceClient;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class AnalyticsGraphqlController {

    private final AnalyticsServiceClient analyticsServiceClient;

    public AnalyticsGraphqlController(AnalyticsServiceClient analyticsServiceClient) {
        this.analyticsServiceClient = analyticsServiceClient;
    }

    @QueryMapping
    public Mono<StreamMetricsSummary> streamMetricsSummary(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") int windowMinutes) {
        return analyticsServiceClient.summary(streamer, streamSessionId, windowMinutes);
    }

    @QueryMapping
    public Mono<List<StreamMetricBucket>> streamMetricsTimeseries(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") int windowMinutes,
            @Argument("bucketSeconds") int bucketSeconds) {
        return analyticsServiceClient.timeseries(streamer, streamSessionId, windowMinutes, bucketSeconds);
    }

    @QueryMapping
    public Mono<List<SponsorExposureMetric>> sponsorExposureMetrics(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") int windowMinutes) {
        return analyticsServiceClient.sponsorExposure(streamer, streamSessionId, windowMinutes);
    }

    @QueryMapping
    public Mono<BrandSafetyMetrics> brandSafetyMetrics(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") int windowMinutes) {
        return analyticsServiceClient.risk(streamer, streamSessionId, windowMinutes);
    }
}
