package com.streamsense.chatservice.controller;

import com.streamsense.chatservice.api.ChatIngestRequest;
import com.streamsense.chatservice.api.ChatIngestResponse;
import com.streamsense.chatservice.config.CorrelationIdFilter;
import com.streamsense.chatservice.service.ChatEventIngestService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatIngestController {

    private final ChatEventIngestService ingestService;

    public ChatIngestController(ChatEventIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<ChatIngestResponse> ingest(
            @Valid @RequestBody ChatIngestRequest req,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_KEY, required = false) String legacyCorrelationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {

        String eventId = ingestService.ingestSynthetic(
                req,
                firstNonBlank(correlationId, legacyCorrelationId, MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)),
                traceparent);

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
