package com.streamsense.apigateway.graphql;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;

import com.streamsense.apigateway.events.ChatMessageEvent;
import com.streamsense.apigateway.subscriptions.ChatSubscriptionBus;

import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class ChatGraphqlController {

    private static final Logger log = LoggerFactory.getLogger(ChatGraphqlController.class);
    private final ChatSubscriptionBus bus;

    public ChatGraphqlController(ChatSubscriptionBus bus) {
        this.bus = bus;
    }

    @SubscriptionMapping("onChatMessage")
    public Flux<ChatMessageEvent> onChatMessage(@Argument("streamer") String streamer) {
        log.info("onChatMessage subscription started streamer={}", streamer);
        return bus.flux()
                .filter(evt -> streamer.equals(evt.getStreamer()));
    }
}