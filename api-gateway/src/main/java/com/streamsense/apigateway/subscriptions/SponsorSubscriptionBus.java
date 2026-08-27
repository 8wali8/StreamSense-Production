package com.streamsense.apigateway.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.SponsorDetectionEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class SponsorSubscriptionBus {

    private static final Logger log = LoggerFactory.getLogger(SponsorSubscriptionBus.class);

    private final Sinks.Many<SponsorDetectionEvent> sink = Sinks.many().replay().latest();

    public void publish(SponsorDetectionEvent event) {
        Sinks.EmitResult result = sink.tryEmitNext(event);
        if (result.isFailure()) {
            log.warn("failed to emit sponsor eventId={} streamer={} result={}",
                    event.getDetectionEventId(), event.getStreamer(), result);
        }
    }

    public Flux<SponsorDetectionEvent> flux() {
        return sink.asFlux();
    }
}
