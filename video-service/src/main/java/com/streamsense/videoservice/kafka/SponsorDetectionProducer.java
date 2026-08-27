package com.streamsense.videoservice.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.streamsense.videoservice.events.SponsorDetectionEvent;

@Component
public class SponsorDetectionProducer {

    private final KafkaTemplate<String, SponsorDetectionEvent> kafkaTemplate;
    private final String topic;

    public SponsorDetectionProducer(
            KafkaTemplate<String, SponsorDetectionEvent> kafkaTemplate,
            @Value("${streamsense.topics.sponsorDetections}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(SponsorDetectionEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, SponsorDetectionEvent> record = new ProducerRecord<>(topic, event.getStreamer(), event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record);
    }
}
