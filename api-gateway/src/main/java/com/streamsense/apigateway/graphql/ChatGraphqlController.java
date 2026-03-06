package com.streamsense.apigateway.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.streamsense.apigateway.events.ChatMessageEvent;
import com.streamsense.apigateway.subscriptions.ChatSubscriptionBus;

import reactor.core.publisher.Flux;

@Controller
public class ChatGraphqlController {

    private final ChatSubscriptionBus bus;

    public ChatGraphqlController(ChatSubscriptionBus bus) {
        this.bus = bus;
    }

    @QueryMapping
    public String health() {
        return "ok";
    }

    @SubscriptionMapping
    public Flux<ChatMessageEvent> onChatMessage(@Argument String streamer) {
        return bus.flux().filter(evt -> streamer.equals(evt.getStreamer()));
    }
}