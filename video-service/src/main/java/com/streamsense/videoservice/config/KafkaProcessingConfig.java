package com.streamsense.videoservice.config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import com.streamsense.videoservice.events.FrameData;
import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.metrics.VideoMetrics;

@Configuration
public class KafkaProcessingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProcessingConfig.class);

    @Bean
    public ProducerFactory<String, FrameData> frameProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return new DefaultKafkaProducerFactory<>(jsonProducerProps(bootstrapServers));
    }

    @Bean
    public KafkaTemplate<String, FrameData> frameKafkaTemplate(
            ProducerFactory<String, FrameData> frameProducerFactory) {
        return new KafkaTemplate<>(frameProducerFactory);
    }

    @Bean
    public ProducerFactory<String, SponsorDetectionEvent> sponsorDetectionProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return new DefaultKafkaProducerFactory<>(jsonProducerProps(bootstrapServers));
    }

    @Bean
    public KafkaTemplate<String, SponsorDetectionEvent> sponsorDetectionKafkaTemplate(
            ProducerFactory<String, SponsorDetectionEvent> sponsorDetectionProducerFactory) {
        return new KafkaTemplate<>(sponsorDetectionProducerFactory);
    }

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
            VideoMetrics videoMetrics) {
        DeadLetterPublishingRecoverer delegate = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(properties.getTopics().getVideoFramesDlt(), record.partition()));

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            String correlationId = headerAsString(record, "correlationId");
            videoMetrics.incrementDeadLetter();
            log.error(
                    "dead-lettering video frame event topic={} partition={} offset={} correlationId={} exception={} message={}",
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
                videoMetrics.incrementRetry();
                log.warn(
                        "retrying video frame processing topic={} partition={} offset={} attempt={} exception={} message={}",
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

    private Map<String, Object> jsonProducerProps(String bootstrapServers) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return configProps;
    }

    private String headerAsString(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
