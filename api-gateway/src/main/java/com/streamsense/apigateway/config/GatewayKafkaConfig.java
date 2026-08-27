package com.streamsense.apigateway.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.streamsense.apigateway.events.ChatMessageEvent;
import com.streamsense.apigateway.events.SponsorDetectionEvent;
import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.events.TranscriptSegmentEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;

@Configuration
public class GatewayKafkaConfig {

    @Bean
    public ConsumerFactory<String, ChatMessageEvent> chatConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:latest}") String autoOffsetReset) {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(bootstrapServers, groupId, autoOffsetReset),
                new StringDeserializer(),
                new JsonDeserializer<>(ChatMessageEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatMessageEvent> chatKafkaListenerContainerFactory(
            ConsumerFactory<String, ChatMessageEvent> chatConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, ChatMessageEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(chatConsumerFactory);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor<>());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, SentimentAnalysisEvent> sentimentConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:latest}") String autoOffsetReset) {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(bootstrapServers, groupId, autoOffsetReset),
                new StringDeserializer(),
                new JsonDeserializer<>(SentimentAnalysisEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SentimentAnalysisEvent> sentimentKafkaListenerContainerFactory(
            ConsumerFactory<String, SentimentAnalysisEvent> sentimentConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, SentimentAnalysisEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sentimentConsumerFactory);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor<>());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, SponsorDetectionEvent> sponsorConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:latest}") String autoOffsetReset) {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(bootstrapServers, groupId, autoOffsetReset),
                new StringDeserializer(),
                new JsonDeserializer<>(SponsorDetectionEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SponsorDetectionEvent> sponsorKafkaListenerContainerFactory(
            ConsumerFactory<String, SponsorDetectionEvent> sponsorConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, SponsorDetectionEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sponsorConsumerFactory);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor<>());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, TranscriptSegmentEvent> transcriptSegmentConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:latest}") String autoOffsetReset) {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(bootstrapServers, groupId, autoOffsetReset),
                new StringDeserializer(),
                new JsonDeserializer<>(TranscriptSegmentEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TranscriptSegmentEvent> transcriptSegmentKafkaListenerContainerFactory(
            ConsumerFactory<String, TranscriptSegmentEvent> transcriptSegmentConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TranscriptSegmentEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transcriptSegmentConsumerFactory);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor<>());
        return factory;
    }

    @Bean
    public ConsumerFactory<String, TranscriptSentimentEvent> transcriptSentimentConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset:latest}") String autoOffsetReset) {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(bootstrapServers, groupId, autoOffsetReset),
                new StringDeserializer(),
                new JsonDeserializer<>(TranscriptSentimentEvent.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TranscriptSentimentEvent> transcriptSentimentKafkaListenerContainerFactory(
            ConsumerFactory<String, TranscriptSentimentEvent> transcriptSentimentConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TranscriptSentimentEvent> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transcriptSentimentConsumerFactory);
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor<>());
        return factory;
    }

    private Map<String, Object> baseConsumerProps(String bootstrapServers, String groupId, String autoOffsetReset) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return props;
    }
}
