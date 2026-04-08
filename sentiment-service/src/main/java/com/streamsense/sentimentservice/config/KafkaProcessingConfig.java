package com.streamsense.sentimentservice.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
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
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

import com.streamsense.sentimentservice.metrics.SentimentMetrics;

@Configuration
public class KafkaProcessingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProcessingConfig.class);

    @Bean
    public ProducerFactory<String, Object> deadLetterProducerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate(ProducerFactory<String, Object> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> deadLetterKafkaTemplate,
            StreamSenseProperties properties,
            SentimentMetrics sentimentMetrics) {
        DeadLetterPublishingRecoverer delegate = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(properties.getTopics().getChatMessagesDlt(), record.partition()));

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            String correlationId = headerAsString(record, "correlationId");
            sentimentMetrics.incrementDeadLetter();
            log.error(
                    "dead-lettering chat event topic={} partition={} offset={} correlationId={} exception={} message={}",
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
                sentimentMetrics.incrementRetry();
                log.warn(
                        "retrying sentiment processing topic={} partition={} offset={} attempt={} exception={} message={}",
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

    private String headerAsString(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
