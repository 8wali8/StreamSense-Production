package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.AnalyticsRange;
import com.streamsense.apigateway.analytics.BrandSafetyMetrics;
import com.streamsense.apigateway.analytics.SponsorExposureMetric;
import com.streamsense.apigateway.analytics.StreamMetricBucket;
import com.streamsense.apigateway.analytics.StreamMetricsSummary;
import com.streamsense.apigateway.analytics.StreamSession;
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
            @Argument("windowMinutes") Integer windowMinutes,
            @Argument("sessionId") String sessionId,
            @Argument("from") Double from,
            @Argument("to") Double to) {
        return analyticsServiceClient.summary(
                streamer, streamSessionId, windowMinutes, AnalyticsRange.of(sessionId, from, to));
    }

    @QueryMapping
    public Mono<List<StreamMetricBucket>> streamMetricsTimeseries(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") Integer windowMinutes,
            @Argument("bucketSeconds") int bucketSeconds,
            @Argument("sessionId") String sessionId,
            @Argument("from") Double from,
            @Argument("to") Double to) {
        return analyticsServiceClient.timeseries(
                streamer, streamSessionId, windowMinutes, bucketSeconds, AnalyticsRange.of(sessionId, from, to));
    }

    @QueryMapping
    public Mono<List<SponsorExposureMetric>> sponsorExposureMetrics(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") Integer windowMinutes,
            @Argument("sessionId") String sessionId,
            @Argument("from") Double from,
            @Argument("to") Double to) {
        return analyticsServiceClient.sponsorExposure(
                streamer, streamSessionId, windowMinutes, AnalyticsRange.of(sessionId, from, to));
    }

    @QueryMapping
    public Mono<BrandSafetyMetrics> brandSafetyMetrics(
            @Argument("streamer") String streamer,
            @Argument("streamSessionId") String streamSessionId,
            @Argument("windowMinutes") Integer windowMinutes,
            @Argument("sessionId") String sessionId,
            @Argument("from") Double from,
            @Argument("to") Double to) {
        return analyticsServiceClient.risk(
                streamer, streamSessionId, windowMinutes, AnalyticsRange.of(sessionId, from, to));
    }

    @QueryMapping
    public Mono<List<StreamSession>> sessions(
            @Argument("streamer") String streamer,
            @Argument("from") Double from,
            @Argument("to") Double to,
            @Argument("limit") Integer limit) {
        return analyticsServiceClient.sessions(
                streamer, from == null ? null : from.longValue(), to == null ? null : to.longValue(), limit);
    }

    @QueryMapping
    public Mono<StreamSession> session(@Argument("id") String id) {
        long sessionId;
        try {
            sessionId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            return Mono.empty();
        }
        return analyticsServiceClient.session(sessionId);
    }
}
