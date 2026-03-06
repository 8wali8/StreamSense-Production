package com.streamsense.chatservice.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.streamsense.chatservice.events.ChatMessageEvent;

@Component
public class ChatKafkaProducer {

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final String chatTopic;

    public ChatKafkaProducer(
            KafkaTemplate<String, ChatMessageEvent> kafkaTemplate,
            @Value("${streamsense.topics.chatMessages}") String chatTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.chatTopic = chatTopic;
    }

    public void publish(ChatMessageEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, ChatMessageEvent> record = new ProducerRecord<>(chatTopic, event.getStreamer(), event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }
        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record);
    }
}