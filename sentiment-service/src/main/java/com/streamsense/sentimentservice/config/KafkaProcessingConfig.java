package com.streamsense.sentimentservice.config;

import com.streamsense.sentimentservice.events.TranscriptSegmentEvent;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaProcessingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaProcessingConfig.class);

    @Bean
    public ProducerFactory<String, Object> deadLetterProducerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, Object> deadLetterKafkaTemplate(
            ProducerFactory<String, Object> deadLetterProducerFactory) {
        return new KafkaTemplate<>(deadLetterProducerFactory);
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> deadLetterKafkaTemplate,
            StreamSenseProperties properties,
            SentimentMetrics sentimentMetrics) {
        DeadLetterPublishingRecoverer delegate = new DeadLetterPublishingRecoverer(
                deadLetterKafkaTemplate,
                (record, ex) -> new TopicPartition(deadLetterTopic(record, properties), record.partition()));

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
                new FixedBackOff(
                        properties.getProcessing().getRetryBackoffMs(),
                        properties.getProcessing().getMaxRetries()));

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

    @Bean
    public ConsumerFactory<String, TranscriptSegmentEvent> transcriptSegmentConsumerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> config = kafkaProperties.buildConsumerProperties();
        config.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "sentiment-service");
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TranscriptSegmentEvent.class.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaConsumerFactory<>(
                config, new StringDeserializer(), new JsonDeserializer<>(TranscriptSegmentEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TranscriptSegmentEvent>
            transcriptSegmentKafkaListenerContainerFactory(
                    ConsumerFactory<String, TranscriptSegmentEvent> transcriptSegmentConsumerFactory,
                    CommonErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, TranscriptSegmentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transcriptSegmentConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    private String deadLetterTopic(ConsumerRecord<?, ?> record, StreamSenseProperties properties) {
        if (record.topic().equals(properties.getTopics().getTranscriptSegments())) {
            return properties.getTopics().getTranscriptSegmentsDlt();
        }
        return properties.getTopics().getChatMessagesDlt();
    }

    private String headerAsString(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
