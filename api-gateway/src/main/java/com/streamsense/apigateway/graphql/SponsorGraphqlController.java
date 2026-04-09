package com.streamsense.apigateway.graphql;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.streamsense.apigateway.client.VideoServiceClient;
import com.streamsense.apigateway.events.SponsorDetectionEvent;
import com.streamsense.apigateway.subscriptions.SponsorSubscriptionBus;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller
public class SponsorGraphqlController {

    private static final Logger log = LoggerFactory.getLogger(SponsorGraphqlController.class);

    private final VideoServiceClient videoServiceClient;
    private final SponsorSubscriptionBus sponsorSubscriptionBus;

    public SponsorGraphqlController(VideoServiceClient videoServiceClient,
            SponsorSubscriptionBus sponsorSubscriptionBus) {
        this.videoServiceClient = videoServiceClient;
        this.sponsorSubscriptionBus = sponsorSubscriptionBus;
    }

    @QueryMapping
    public Mono<List<SponsorDetectionEvent>> sponsorDetections(
            @Argument("streamer") String streamer,
            @Argument("limit") int limit) {
        return videoServiceClient.recentDetections(streamer, limit);
    }

    @SubscriptionMapping("onSponsorDetection")
    public Flux<SponsorDetectionEvent> onSponsorDetection(@Argument("streamer") String streamer) {
        log.info("onSponsorDetection subscription started streamer={}", streamer);
        return sponsorSubscriptionBus.flux()
                .filter(event -> streamer.equals(event.getStreamer()));
    }
}
