package com.streamsense.analyticsservice.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.events.SentimentAnalysisEvent;
import com.streamsense.analyticsservice.events.SponsorDetectionEvent;
import com.streamsense.analyticsservice.events.TranscriptSentimentEvent;
import com.streamsense.analyticsservice.metrics.AnalyticsMetrics;
import com.streamsense.analyticsservice.service.MetricAggregationService;

@Component
public class AnalyticsKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsKafkaConsumer.class);

    private final ObjectMapper objectMapper;
    private final StreamSenseProperties properties;
    private final MetricAggregationService aggregationService;
    private final AnalyticsMetrics metrics;

    public AnalyticsKafkaConsumer(ObjectMapper objectMapper, StreamSenseProperties properties,
            MetricAggregationService aggregationService, AnalyticsMetrics metrics) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.aggregationService = aggregationService;
        this.metrics = metrics;
    }

    @KafkaListener(topics = "${streamsense.topics.chatMessages:stream.chat.messages}", groupId = "analytics-service-chat")
    public void onChatMessage(ConsumerRecord<String, String> record) {
        process(record, ChatMessageEvent.class,
                event -> aggregationService.aggregateChatMessage(properties.getTopics().getChatMessages(), event));
    }

    @KafkaListener(topics = "${streamsense.topics.sentimentEvents:stream.sentiment.events}", groupId = "analytics-service-sentiment")
    public void onSentiment(ConsumerRecord<String, String> record) {
        process(record, SentimentAnalysisEvent.class,
                event -> aggregationService.aggregateChatSentiment(properties.getTopics().getSentimentEvents(), event));
    }

    @KafkaListener(topics = "${streamsense.topics.transcriptSentimentEvents:stream.transcript.sentiment.events}", groupId = "analytics-service-transcript-sentiment")
    public void onTranscriptSentiment(ConsumerRecord<String, String> record) {
        process(record, TranscriptSentimentEvent.class,
                event -> aggregationService.aggregateTranscriptSentiment(properties.getTopics().getTranscriptSentimentEvents(), event));
    }

    @KafkaListener(topics = "${streamsense.topics.sponsorDetections:stream.sponsor.detections}", groupId = "analytics-service-sponsor")
    public void onSponsorDetection(ConsumerRecord<String, String> record) {
        process(record, SponsorDetectionEvent.class,
                event -> aggregationService.aggregateSponsorDetection(properties.getTopics().getSponsorDetections(), event));
    }

    private <T> void process(ConsumerRecord<String, String> record, Class<T> eventType, Processor<T> processor) {
        try {
            T event = objectMapper.readValue(record.value(), eventType);
            processor.process(event);
        } catch (Exception ex) {
            metrics.eventFailed(record.topic());
            log.warn("analytics event processing failed topic={} partition={} offset={} type={} error={}",
                    record.topic(), record.partition(), record.offset(), eventType.getSimpleName(), ex.toString());
        }
    }

    @FunctionalInterface
    private interface Processor<T> {
        boolean process(T event);
    }
}
