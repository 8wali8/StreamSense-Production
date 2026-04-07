package com.streamsense.chatservice.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.streamsense.chatservice.api.ChatIngestRequest;
import com.streamsense.chatservice.api.ChatIngestResponse;
import com.streamsense.chatservice.config.CorrelationIdFilter;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.kafka.ChatKafkaProducer;
import com.streamsense.chatservice.metrics.ChatMetrics;

@RestController
@RequestMapping("/api/chat")
public class ChatIngestController {

    private final ChatKafkaProducer producer;
    private final ChatMetrics chatMetrics;

    public ChatIngestController(ChatKafkaProducer producer, ChatMetrics chatMetrics) {
        this.producer = producer;
        this.chatMetrics = chatMetrics;
    }

    @PostMapping("/ingest")
    public ResponseEntity<ChatIngestResponse> ingest(
            @Valid @RequestBody ChatIngestRequest req,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_KEY, required = false) String legacyCorrelationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {

        String eventId = UUID.randomUUID().toString();

        ChatMessageEvent event = new ChatMessageEvent(
                eventId,
                req.getStreamer(),
                req.getUser(),
                req.getMessage(),
                req.getTimestamp());

        producer.publish(event, firstNonBlank(correlationId, legacyCorrelationId, MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)), traceparent);
        chatMetrics.incrementChatIngest();

        return ResponseEntity.ok(new ChatIngestResponse(eventId));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
