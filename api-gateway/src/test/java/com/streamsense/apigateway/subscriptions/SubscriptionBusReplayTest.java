package com.streamsense.apigateway.subscriptions;

import com.streamsense.apigateway.events.ChatMessageEvent;
import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.events.SponsorDetectionEvent;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class SubscriptionBusReplayTest {

    @Test
    void chatBusReplaysLatestEventToLateSubscriber() {
        ChatSubscriptionBus bus = new ChatSubscriptionBus();
        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-1");
        event.setStreamer("test");
        event.setUser("u1");
        event.setMessage("hello");
        event.setTimestamp(1710000000000L);

        bus.publish(event);

        StepVerifier.create(bus.flux().take(1))
                .expectNextMatches(received -> "evt-1".equals(received.getEventId()))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void sentimentBusReplaysLatestEventToLateSubscriber() {
        SentimentSubscriptionBus bus = new SentimentSubscriptionBus();
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId("sent-1");
        event.setStreamer("test");
        event.setLabel("POSITIVE");

        bus.publish(event);

        StepVerifier.create(bus.flux().take(1))
                .expectNextMatches(received -> "sent-1".equals(received.getSentimentEventId()))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void sponsorBusReplaysLatestEventToLateSubscriber() {
        SponsorSubscriptionBus bus = new SponsorSubscriptionBus();
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId("det-1");
        event.setStreamer("test");
        event.setSponsor("Nike");

        bus.publish(event);

        StepVerifier.create(bus.flux().take(1))
                .expectNextMatches(received -> "det-1".equals(received.getDetectionEventId()))
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }
}
