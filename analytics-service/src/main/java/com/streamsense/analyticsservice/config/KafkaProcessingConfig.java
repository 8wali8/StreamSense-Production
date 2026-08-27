package com.streamsense.analyticsservice.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import com.streamsense.analyticsservice.metrics.AnalyticsMetrics;

@Configuration
public class KafkaProcessingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProcessingConfig.class);

    @Bean
    public ProducerFactory<String, String> deadLetterProducerFactory(KafkaProperties kafkaProperties) {
        // Analytics consumes raw String payloads, so the dead-letter copy must stay a raw String too.
        Map<String, Object> config = kafkaProperties.buildProducerProperties();
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> deadLetterKafkaTemplate(ProducerFactory<String, String> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> deadLetterKafkaTemplate,
            StreamSenseProperties properties,
            AnalyticsMetrics analyticsMetrics) {
        DeadLetterPublishingRecoverer delegate = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(deadLetterTopic(record.topic(), properties.getTopics()), record.partition()));

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            String correlationId = headerAsString(record, "correlationId");
            analyticsMetrics.eventFailed(record.topic());
            analyticsMetrics.eventDeadLettered(record.topic());
            log.error(
                    "dead-lettering analytics event topic={} partition={} offset={} correlationId={} exception={} message={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    correlationId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage(),
                    ex);
            delegate.accept(record, ex);
        };

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(properties.getProcessing().getRetryBackoffMs(), properties.getProcessing().getMaxRetries()));

        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, IllegalStateException.class);
        errorHandler.setCommitRecovered(true);
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            if (deliveryAttempt > 1) {
                analyticsMetrics.eventRetried(record.topic());
                log.warn(
                        "retrying analytics event processing topic={} partition={} offset={} attempt={} exception={} message={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        deliveryAttempt,
                        ex.getClass().getSimpleName(),
                        ex.getMessage());
            }
        });

        return errorHandler;
    }

    static String deadLetterTopic(String sourceTopic, StreamSenseProperties.Topics topics) {
        if (sourceTopic.equals(topics.getChatMessages())) {
            return topics.getChatMessagesDlt();
        }
        if (sourceTopic.equals(topics.getSentimentEvents())) {
            return topics.getSentimentEventsDlt();
        }
        if (sourceTopic.equals(topics.getTranscriptSentimentEvents())) {
            return topics.getTranscriptSentimentEventsDlt();
        }
        if (sourceTopic.equals(topics.getSponsorDetections())) {
            return topics.getSponsorDetectionsDlt();
        }
        return sourceTopic + ".analytics.dlt";
    }

    private String headerAsString(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
