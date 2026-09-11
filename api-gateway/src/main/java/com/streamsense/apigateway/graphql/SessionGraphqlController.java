package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.Deal;
import com.streamsense.apigateway.analytics.SessionSummary;
import com.streamsense.apigateway.analytics.SponsorMoments;
import com.streamsense.apigateway.analytics.StreamSession;
import com.streamsense.apigateway.client.AnalyticsServiceClient;
import com.streamsense.apigateway.client.RangePaging;
import com.streamsense.apigateway.client.SentimentServiceClient;
import com.streamsense.apigateway.client.VideoServiceClient;
import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.events.SponsorDetectionEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;
import graphql.GraphQLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/** The session report: the numbers from analytics-service, the timeline composed from three stores. */
@Controller
public class SessionGraphqlController {

    private static final int RANGE_LIMIT = 2000;
    private static final int BUCKET_SECONDS = 60;

    private final AnalyticsServiceClient analytics;
    private final SentimentServiceClient sentiment;
    private final VideoServiceClient video;
    // Mirrors analytics-service's streamsense.analytics.minimum-sponsor-confidence (same env var in config-repo).
    private final double minimumSponsorConfidence;

    public SessionGraphqlController(
            AnalyticsServiceClient analytics,
            SentimentServiceClient sentiment,
            VideoServiceClient video,
            @Value("${streamsense.gateway.sponsor-moments.minimum-confidence:0.50}") double minimumSponsorConfidence) {
        this.analytics = analytics;
        this.sentiment = sentiment;
        this.video = video;
        this.minimumSponsorConfidence = minimumSponsorConfidence;
    }

    @QueryMapping
    public Mono<SessionSummary> sessionSummary(
            @Argument("sessionId") String sessionId,
            @Argument("sponsor") String sponsor,
            @Argument("chatCommand") String chatCommand,
            @Argument("trackedLinkHost") String trackedLinkHost,
            @Argument("cpmPer30sEquivalent") Double cpm,
            @Argument("hostReadRatePer1000") Double hostReadRate,
            GraphQLContext context) {
        Long id = parseId(sessionId);
        if (id == null) {
            return Mono.empty();
        }
        Deal shared = ShareLinkInterceptor.sharedDeal(context);
        if (shared != null) {
            return sharedSummary(id, shared);
        }
        return analytics.sessionSummary(id, sponsor, chatCommand, trackedLinkHost, cpm, hostReadRate);
    }

    /** Under a share link the report is always about the deal's sponsor at the deal's terms, and only inside the deal. */
    private Mono<SessionSummary> sharedSummary(long id, Deal shared) {
        return analytics
                .sessionSummary(id, shared.sponsor(), null, null, null, null)
                .filter(summary -> summary.dealId() != null && summary.dealId() == shared.id());
    }

    @QueryMapping
    public Mono<SponsorMoments> sponsorMoments(
            @Argument("sessionId") String sessionId, @Argument("sponsor") String sponsor, GraphQLContext context) {
        Long id = parseId(sessionId);
        if (id == null) {
            return Mono.empty();
        }
        Deal shared = ShareLinkInterceptor.sharedDeal(context);
        if (shared != null) {
            return sharedSummary(id, shared).flatMap(summary -> moments(summary.session(), shared.sponsor()));
        }
        return analytics.session(id).flatMap(session -> moments(session, sponsor));
    }

    private Mono<SponsorMoments> moments(StreamSession session, String sponsor) {
        long id = session.id();
        long from = session.startedAt();
        long to = session.endedAt() == null ? session.startedAt() + session.durationMs() : session.endedAt();
        String chosen = sponsor == null || sponsor.isBlank() ? null : sponsor.trim();
        Mono<String> resolvedSponsor = chosen != null
                ? Mono.just(chosen)
                : analytics
                        .sessionSummary(id, null, null, null, null, null)
                        .map(summary -> summary.sponsor() == null ? "" : summary.sponsor())
                        .defaultIfEmpty("");
        return resolvedSponsor.flatMap(name -> {
            String sponsorOrNull = name.isBlank() ? null : name;
            // Each store answers a page at a time; RangePaging walks them so a busy stream keeps every event.
            return Mono.zip(
                            RangePaging.all(
                                    from,
                                    to,
                                    RANGE_LIMIT,
                                    (f, t) -> video.detectionsInRange(session.streamer(), f, t, RANGE_LIMIT),
                                    SponsorDetectionEvent::getCapturedAt,
                                    SponsorDetectionEvent::getDetectionEventId),
                            RangePaging.all(
                                    from,
                                    to,
                                    RANGE_LIMIT,
                                    (f, t) -> sentiment.sponsorSentimentInRange(
                                            session.streamer(), sponsorOrNull, f, t, RANGE_LIMIT),
                                    SentimentAnalysisEvent::getChatTimestamp,
                                    SentimentAnalysisEvent::getSentimentEventId),
                            RangePaging.all(
                                    from,
                                    to,
                                    RANGE_LIMIT,
                                    (f, t) -> sentiment.sponsorTranscriptSentimentInRange(
                                            session.streamer(), sponsorOrNull, f, t, RANGE_LIMIT),
                                    TranscriptSentimentEvent::getSegmentEndedAt,
                                    TranscriptSentimentEvent::getSentimentEventId),
                            analytics.timeseriesInRange(session.streamer(), from, to, BUCKET_SECONDS))
                    .map(parts -> compose(session, sponsorOrNull, parts));
        });
    }

    private SponsorMoments compose(
            StreamSession session,
            String sponsor,
            reactor.util.function.Tuple4<
                            java.util.List<com.streamsense.apigateway.events.SponsorDetectionEvent>,
                            java.util.List<com.streamsense.apigateway.events.SentimentAnalysisEvent>,
                            java.util.List<com.streamsense.apigateway.events.TranscriptSentimentEvent>,
                            java.util.List<com.streamsense.apigateway.analytics.StreamMetricBucket>>
                    parts) {
        return SponsorMomentsComposer.compose(
                session,
                sponsor,
                parts.getT1(),
                parts.getT2(),
                parts.getT3(),
                parts.getT4(),
                SponsorMomentsComposer.DEFAULT_GAP_MS,
                minimumSponsorConfidence);
    }

    private static Long parseId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(sessionId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
