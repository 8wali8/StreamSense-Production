package com.streamsense.analyticsservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.events.SentimentAnalysisEvent;
import com.streamsense.analyticsservice.events.SponsorDetectionEvent;
import com.streamsense.analyticsservice.events.TranscriptSentimentEvent;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final StreamSenseProperties properties;
    private final MetricAggregationService aggregationService;

    public AnalyticsKafkaConsumer(
            ObjectMapper objectMapper, StreamSenseProperties properties, MetricAggregationService aggregationService) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.aggregationService = aggregationService;
    }

    @KafkaListener(
            topics = "${streamsense.topics.chatMessages:stream.chat.messages}",
            groupId = "analytics-service-chat")
    public void onChatMessage(ConsumerRecord<String, String> record) {
        process(
                record,
                ChatMessageEvent.class,
                event -> aggregationService.aggregateChatMessage(
                        properties.getTopics().getChatMessages(), event));
    }

    @KafkaListener(
            topics = "${streamsense.topics.sentimentEvents:stream.sentiment.events}",
            groupId = "analytics-service-sentiment")
    public void onSentiment(ConsumerRecord<String, String> record) {
        process(
                record,
                SentimentAnalysisEvent.class,
                event -> aggregationService.aggregateChatSentiment(
                        properties.getTopics().getSentimentEvents(), event));
    }

    @KafkaListener(
            topics = "${streamsense.topics.transcriptSentimentEvents:stream.transcript.sentiment.events}",
            groupId = "analytics-service-transcript-sentiment")
    public void onTranscriptSentiment(ConsumerRecord<String, String> record) {
        process(
                record,
                TranscriptSentimentEvent.class,
                event -> aggregationService.aggregateTranscriptSentiment(
                        properties.getTopics().getTranscriptSentimentEvents(), event));
    }

    @KafkaListener(
            topics = "${streamsense.topics.sponsorDetections:stream.sponsor.detections}",
            groupId = "analytics-service-sponsor")
    public void onSponsorDetection(ConsumerRecord<String, String> record) {
        process(
                record,
                SponsorDetectionEvent.class,
                event -> aggregationService.aggregateSponsorDetection(
                        properties.getTopics().getSponsorDetections(), event));
    }

    // Failures propagate to the container's CommonErrorHandler (KafkaProcessingConfig): malformed or invalid
    // events are dead-lettered immediately, anything else (e.g. a transient JDBC failure) is retried first.
    private <T> void process(ConsumerRecord<String, String> record, Class<T> eventType, Processor<T> processor) {
        T event;
        try {
            event = objectMapper.readValue(record.value(), eventType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "malformed " + eventType.getSimpleName() + " payload: " + ex.getOriginalMessage(), ex);
        }
        processor.process(event);
    }

    @FunctionalInterface
    private interface Processor<T> {
        boolean process(T event);
    }
}
