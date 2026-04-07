package com.streamsense.apigateway.subscriptions;

import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.SentimentAnalysisEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class SentimentSubscriptionBus {

    private final Sinks.Many<SentimentAnalysisEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(SentimentAnalysisEvent event) {
        sink.tryEmitNext(event);
    }

    public Flux<SentimentAnalysisEvent> flux() {
        return sink.asFlux();
    }
}
