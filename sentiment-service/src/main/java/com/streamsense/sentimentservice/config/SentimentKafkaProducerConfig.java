package com.streamsense.sentimentservice.config;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class SentimentKafkaProducerConfig {

    @Bean
    public ProducerFactory<String, SentimentAnalysisEvent> sentimentProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, SentimentAnalysisEvent> sentimentKafkaTemplate(
            ProducerFactory<String, SentimentAnalysisEvent> sentimentProducerFactory) {
        return new KafkaTemplate<>(sentimentProducerFactory);
    }

    @Bean
    public ProducerFactory<String, TranscriptSentimentEvent> transcriptSentimentProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, TranscriptSentimentEvent> transcriptSentimentKafkaTemplate(
            ProducerFactory<String, TranscriptSentimentEvent> transcriptSentimentProducerFactory) {
        return new KafkaTemplate<>(transcriptSentimentProducerFactory);
    }
}
