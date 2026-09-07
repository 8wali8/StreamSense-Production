package com.streamsense.sentimentservice.kafka;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SentimentKafkaProducer {

    private final KafkaTemplate<String, SentimentAnalysisEvent> kafkaTemplate;
    private final KafkaTemplate<String, TranscriptSentimentEvent> transcriptKafkaTemplate;
    private final String sentimentTopic;
    private final String transcriptSentimentTopic;

    public SentimentKafkaProducer(
            @Qualifier("sentimentKafkaTemplate") KafkaTemplate<String, SentimentAnalysisEvent> kafkaTemplate,
            @Qualifier("transcriptSentimentKafkaTemplate")
                    KafkaTemplate<String, TranscriptSentimentEvent> transcriptKafkaTemplate,
            @Value("${streamsense.topics.sentimentEvents}") String sentimentTopic,
            @Value("${streamsense.topics.transcriptSentimentEvents:stream.transcript.sentiment.events}")
                    String transcriptSentimentTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.transcriptKafkaTemplate = transcriptKafkaTemplate;
        this.sentimentTopic = sentimentTopic;
        this.transcriptSentimentTopic = transcriptSentimentTopic;
    }

    public void publish(SentimentAnalysisEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, SentimentAnalysisEvent> record =
                new ProducerRecord<>(sentimentTopic, event.getStreamer(), event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record);
    }

    public void publishTranscript(TranscriptSentimentEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, TranscriptSentimentEvent> record =
                new ProducerRecord<>(transcriptSentimentTopic, event.getStreamer(), event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        transcriptKafkaTemplate.send(record);
    }
}
