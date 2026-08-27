package com.streamsense.sentimentservice.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.sentimentservice.events.ChatMessageEvent;
import com.streamsense.sentimentservice.service.SentimentService;

@Component
public class ChatMessageConsumer {

    private final SentimentService sentimentService;

    public ChatMessageConsumer(SentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    @KafkaListener(topics = "${streamsense.topics.chatMessages}")
    public void onMessage(ChatMessageEvent event, ConsumerRecord<String, ChatMessageEvent> record) {
        String correlationId = headerAsString(record.headers().lastHeader("correlationId"));
        String traceparent = headerAsString(record.headers().lastHeader("traceparent"));

        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
        if (traceparent != null && !traceparent.isBlank()) {
            MDC.put("traceparent", traceparent);
        }

        try {
            sentimentService.processChatMessage(event, correlationId, traceparent);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("traceparent");
        }
    }

    private String headerAsString(Header header) {
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
