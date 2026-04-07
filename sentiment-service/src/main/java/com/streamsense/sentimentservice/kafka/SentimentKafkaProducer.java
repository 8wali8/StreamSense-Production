package com.streamsense.sentimentservice.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;

@Component
public class SentimentKafkaProducer {

    private final KafkaTemplate<String, SentimentAnalysisEvent> kafkaTemplate;
    private final String sentimentTopic;

    public SentimentKafkaProducer(
            KafkaTemplate<String, SentimentAnalysisEvent> kafkaTemplate,
            @Value("${streamsense.topics.sentimentEvents}") String sentimentTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.sentimentTopic = sentimentTopic;
    }

    public void publish(SentimentAnalysisEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, SentimentAnalysisEvent> record = new ProducerRecord<>(
                sentimentTopic,
                event.getStreamer(),
                event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record);
    }
}
