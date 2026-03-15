package com.streamsense.chatservice.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.streamsense.chatservice.events.SentimentAnalysisEvent;
import com.streamsense.chatservice.metrics.ChatMetrics;

@Component
public class SentimentKafkaProducer {

    private final KafkaTemplate<String, SentimentAnalysisEvent> kafkaTemplate;
    private final String sentimentTopic;
    private final ChatMetrics chatMetrics;

    public SentimentKafkaProducer(
            KafkaTemplate<String, SentimentAnalysisEvent> kafkaTemplate,
            @Value("${streamsense.topics.sentimentEvents}") String sentimentTopic,
            ChatMetrics chatMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.sentimentTopic = sentimentTopic;
        this.chatMetrics = chatMetrics;
    }

    public void publish(SentimentAnalysisEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, SentimentAnalysisEvent> record = new ProducerRecord<>(sentimentTopic,
                event.getStreamer(), event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        chatMetrics.recordKafkaProduce(() -> kafkaTemplate.send(record));
    }
}