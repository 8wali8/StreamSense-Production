package com.streamsense.apigateway.subscriptions;

import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.ChatMessageEvent;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class ChatSubscriptionBus {
    private final Sinks.Many<ChatMessageEvent> sink = Sinks.many().multicast().onBackpressureBuffer();

    public void publish(ChatMessageEvent evt) {
        sink.tryEmitNext(evt);
    }

    public Flux<ChatMessageEvent> flux() {
        return sink.asFlux();
    }
}