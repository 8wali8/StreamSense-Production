package com.streamsense.chatservice.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.streamsense.chatservice.api.ChatIngestRequest;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.kafka.ChatKafkaProducer;
import com.streamsense.chatservice.metrics.ChatMetrics;

@Service
public class ChatEventIngestService {

    private final ChatKafkaProducer producer;
    private final ChatMetrics chatMetrics;

    public ChatEventIngestService(ChatKafkaProducer producer, ChatMetrics chatMetrics) {
        this.producer = producer;
        this.chatMetrics = chatMetrics;
    }

    public String ingestSynthetic(ChatIngestRequest request, String correlationId, String traceparent) {
        String eventId = UUID.randomUUID().toString();
        ChatMessageEvent event = new ChatMessageEvent(
                eventId,
                request.getStreamer(),
                request.getUser(),
                request.getMessage(),
                request.getTimestamp());

        producer.publish(event, correlationId, traceparent);
        chatMetrics.incrementChatIngest();
        return eventId;
    }

    public void ingestTwitch(ChatMessageEvent event) {
        producer.publish(event, null, null);
    }
}
