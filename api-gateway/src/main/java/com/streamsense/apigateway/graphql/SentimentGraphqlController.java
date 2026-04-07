package com.streamsense.apigateway.graphql;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.streamsense.apigateway.client.SentimentServiceClient;
import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.subscriptions.SentimentSubscriptionBus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class SentimentGraphqlController {

    private static final Logger log = LoggerFactory.getLogger(SentimentGraphqlController.class);

    private final SentimentServiceClient sentimentServiceClient;
    private final SentimentSubscriptionBus sentimentSubscriptionBus;

    public SentimentGraphqlController(
            SentimentServiceClient sentimentServiceClient,
            SentimentSubscriptionBus sentimentSubscriptionBus) {
        this.sentimentServiceClient = sentimentServiceClient;
        this.sentimentSubscriptionBus = sentimentSubscriptionBus;
    }

    @QueryMapping
    public Mono<List<SentimentAnalysisEvent>> recentSentiment(
            @Argument("streamer") String streamer,
            @Argument("limit") int limit) {
        return sentimentServiceClient.recentSentiment(streamer, limit);
    }

    @SubscriptionMapping("onSentiment")
    public Flux<SentimentAnalysisEvent> onSentiment(@Argument("streamer") String streamer) {
        log.info("onSentiment subscription started streamer={}", streamer);
        return sentimentSubscriptionBus.flux()
                .filter(event -> streamer.equals(event.getStreamer()));
    }
}
