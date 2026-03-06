package com.streamsense.apigateway.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.events.ChatMessageEvent;
import com.streamsense.apigateway.subscriptions.ChatSubscriptionBus;

@Component
public class ChatKafkaConsumer {

    private final ChatSubscriptionBus bus;

    public ChatKafkaConsumer(ChatSubscriptionBus bus) {
        this.bus = bus;
    }

    @KafkaListener(topics = "${streamsense.topics.chatMessages}")
    public void onMessage(ChatMessageEvent event) {
        System.out.println("gateway consumed eventId=" + event.getEventId() + " streamer=" + event.getStreamer());
        bus.publish(event);
    }
}