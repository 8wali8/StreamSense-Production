package com.streamsense.apigateway.consumer;

import com.streamsense.apigateway.events.ChatMessageEvent;
import com.streamsense.apigateway.subscriptions.ChatSubscriptionBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ChatKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatKafkaConsumer.class);

    private final ChatSubscriptionBus bus;

    public ChatKafkaConsumer(ChatSubscriptionBus bus) {
        this.bus = bus;
    }

    @KafkaListener(
            topics = "${streamsense.topics.chatMessages}",
            containerFactory = "chatKafkaListenerContainerFactory")
    public void onMessage(ChatMessageEvent event) {
        log.info("gateway consumed eventId={} streamer={}", event.getEventId(), event.getStreamer());
        bus.publish(event);
    }
}
