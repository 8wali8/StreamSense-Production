package com.streamsense.apigateway.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.SentimentAnalysisEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class SentimentSubscriptionBus {

    private static final Logger log = LoggerFactory.getLogger(SentimentSubscriptionBus.class);

    private final Sinks.Many<SentimentAnalysisEvent> sink = Sinks.many().replay().latest();

    public void publish(SentimentAnalysisEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("failed to emit sentiment eventId={} streamer={} result={}",
                    event.getSentimentEventId(), event.getStreamer(), result);
        }
    }

    public Flux<SentimentAnalysisEvent> flux() {
        return sink.asFlux();
    }
}
