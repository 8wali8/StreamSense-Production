package com.streamsense.sentimentservice.kafka;

import com.streamsense.sentimentservice.events.TranscriptSegmentEvent;
import com.streamsense.sentimentservice.service.SentimentService;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TranscriptSegmentConsumer {

    private final SentimentService sentimentService;

    public TranscriptSegmentConsumer(SentimentService sentimentService) {
        this.sentimentService = sentimentService;
    }

    @KafkaListener(
            topics = "${streamsense.topics.transcriptSegments:stream.transcript.segments}",
            containerFactory = "transcriptSegmentKafkaListenerContainerFactory")
    public void onMessage(TranscriptSegmentEvent event, ConsumerRecord<String, TranscriptSegmentEvent> record) {
        String correlationId = headerAsString(record.headers().lastHeader("correlationId"));
        String traceparent = headerAsString(record.headers().lastHeader("traceparent"));

        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
        if (traceparent != null && !traceparent.isBlank()) {
            MDC.put("traceparent", traceparent);
        }

        try {
            sentimentService.processTranscriptSegment(event, correlationId, traceparent);
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
