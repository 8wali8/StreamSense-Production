package com.streamsense.apigateway.subscriptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.ChatMessageEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class ChatSubscriptionBus {

    private static final Logger log = LoggerFactory.getLogger(ChatSubscriptionBus.class);

    private final Sinks.Many<ChatMessageEvent> sink = Sinks.many().replay().latest();

    public void publish(ChatMessageEvent evt) {
        Sinks.EmitResult result = sink.tryEmitNext(evt);
        if (result.isFailure()) {
            log.warn("failed to emit chat eventId={} streamer={} result={}", evt.getEventId(), evt.getStreamer(), result);
        }
    }

    public Flux<ChatMessageEvent> flux() {
        return sink.asFlux();
    }
}
