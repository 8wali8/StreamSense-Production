package com.streamsense.videoservice.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.videoservice.config.CorrelationIdFilter;
import com.streamsense.videoservice.events.FrameData;
import com.streamsense.videoservice.metrics.VideoMetrics;
import com.streamsense.videoservice.service.VideoProcessingService;

@Component
public class VideoFrameConsumer {

    private final VideoProcessingService videoProcessingService;
    private final VideoMetrics videoMetrics;

    public VideoFrameConsumer(VideoProcessingService videoProcessingService, VideoMetrics videoMetrics) {
        this.videoProcessingService = videoProcessingService;
        this.videoMetrics = videoMetrics;
    }

    @KafkaListener(topics = "${streamsense.topics.videoFrames}")
    public void onMessage(FrameData event, ConsumerRecord<String, FrameData> record) {
        String correlationId = headerAsString(record.headers().lastHeader("correlationId"));
        String traceparent = headerAsString(record.headers().lastHeader("traceparent"));

        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put(CorrelationIdFilter.CORRELATION_ID_KEY, correlationId);
        }
        if (traceparent != null && !traceparent.isBlank()) {
            MDC.put("traceparent", traceparent);
        }

        try {
            videoMetrics.incrementFramesIngested();
            if ("TWITCH".equalsIgnoreCase(event.getSource())) {
                videoMetrics.incrementFramesFromTwitch();
            }
            videoProcessingService.processFrame(event, correlationId, traceparent);
        } finally {
            MDC.remove(CorrelationIdFilter.CORRELATION_ID_KEY);
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
