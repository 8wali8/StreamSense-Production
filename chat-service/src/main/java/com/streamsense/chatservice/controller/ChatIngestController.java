package com.streamsense.chatservice.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.streamsense.chatservice.api.ChatIngestRequest;
import com.streamsense.chatservice.api.ChatIngestResponse;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.kafka.ChatKafkaProducer;

@RestController
@RequestMapping("/api/chat")
public class ChatIngestController {

    private final ChatKafkaProducer producer;

    public ChatIngestController(ChatKafkaProducer producer) {
        this.producer = producer;
    }

    @PostMapping("/ingest")
    public ResponseEntity<ChatIngestResponse> ingest(
            @Valid @RequestBody ChatIngestRequest req,
            @RequestHeader(value = "correlationId", required = false) String correlationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        String eventId = UUID.randomUUID().toString();

        ChatMessageEvent event = new ChatMessageEvent(
                eventId,
                req.getStreamer(),
                req.getUser(),
                req.getMessage(),
                req.getTimestamp());

        producer.publish(event, correlationId, traceparent);

        return ResponseEntity.ok(new ChatIngestResponse(eventId));
    }
}