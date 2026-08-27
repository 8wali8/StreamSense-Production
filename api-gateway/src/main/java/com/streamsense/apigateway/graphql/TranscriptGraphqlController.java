package com.streamsense.apigateway.graphql;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.streamsense.apigateway.client.SentimentServiceClient;
import com.streamsense.apigateway.events.TranscriptSegmentEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;
import com.streamsense.apigateway.subscriptions.TranscriptSubscriptionBus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class TranscriptGraphqlController {

    private static final Logger log = LoggerFactory.getLogger(TranscriptGraphqlController.class);

    private final SentimentServiceClient sentimentServiceClient;
    private final TranscriptSubscriptionBus transcriptSubscriptionBus;

    public TranscriptGraphqlController(
            SentimentServiceClient sentimentServiceClient,
            TranscriptSubscriptionBus transcriptSubscriptionBus) {
        this.sentimentServiceClient = sentimentServiceClient;
        this.transcriptSubscriptionBus = transcriptSubscriptionBus;
    }

    @QueryMapping
    public Mono<List<TranscriptSegmentEvent>> recentTranscriptSegments(
            @Argument("streamer") String streamer,
            @Argument("limit") int limit) {
        return sentimentServiceClient.recentTranscriptSegments(streamer, limit);
    }

    @QueryMapping
    public Mono<List<TranscriptSentimentEvent>> recentTranscriptSentiment(
            @Argument("streamer") String streamer,
            @Argument("limit") int limit) {
        return sentimentServiceClient.recentTranscriptSentiment(streamer, limit);
    }

    @QueryMapping
    public Mono<List<TranscriptSentimentEvent>> recentSponsorTranscriptSentiment(
            @Argument("streamer") String streamer,
            @Argument("sponsor") String sponsor,
            @Argument("limit") int limit) {
        return sentimentServiceClient.recentSponsorTranscriptSentiment(streamer, sponsor, limit);
    }

    @SubscriptionMapping("onTranscriptSegment")
    public Flux<TranscriptSegmentEvent> onTranscriptSegment(@Argument("streamer") String streamer) {
        log.info("onTranscriptSegment subscription started streamer={}", streamer);
        return transcriptSubscriptionBus.transcriptFlux()
                .filter(event -> streamer.equals(event.getStreamer()));
    }

    @SubscriptionMapping("onTranscriptSentiment")
    public Flux<TranscriptSentimentEvent> onTranscriptSentiment(@Argument("streamer") String streamer) {
        log.info("onTranscriptSentiment subscription started streamer={}", streamer);
        return transcriptSubscriptionBus.sentimentFlux()
                .filter(event -> streamer.equals(event.getStreamer()));
    }

    @SubscriptionMapping("onSponsorTranscriptSentiment")
    public Flux<TranscriptSentimentEvent> onSponsorTranscriptSentiment(
            @Argument("streamer") String streamer,
            @Argument("sponsor") String sponsor) {
        log.info("onSponsorTranscriptSentiment subscription started streamer={} sponsor={}", streamer, sponsor);
        return transcriptSubscriptionBus.sentimentFlux()
                .filter(event -> streamer.equals(event.getStreamer()))
                .filter(TranscriptSentimentEvent::isSponsorRelevant)
                .filter(event -> sponsor == null || sponsor.isBlank()
                        || sponsor.equalsIgnoreCase(event.getMatchedSponsor()));
    }
}
