package com.streamsense.chatservice.consumer;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.chatservice.events.ChatMessageEvent;

@Component
public class ChatMessageLogConsumer {

    @KafkaListener(topics = "${streamsense.topics.chatMessages}", groupId = "${spring.kafka.consumer.group-id:chat-service-log}")
    public void onMessage(ChatMessageEvent event,
            org.apache.kafka.clients.consumer.ConsumerRecord<String, ChatMessageEvent> record) {
        String correlationId = headerAsString(record.headers().lastHeader("correlationId"));
        String traceparent = headerAsString(record.headers().lastHeader("traceparent"));

        System.out.printf(
                "consumed eventId=%s streamer=%s correlationId=%s traceparent=%s message=%s%n",
                event.getEventId(),
                event.getStreamer(),
                correlationId,
                traceparent,
                event.getMessage());
    }

    private String headerAsString(Header header) {
        if (header == null)
            return null;
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}