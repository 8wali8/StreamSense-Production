package com.streamsense.apigateway.graphql;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.streamsense.apigateway.events.SentimentAnalysisEvent;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = { "stream.sentiment.events" })
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=api-gateway-test-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "spring.graphql.websocket.path=/graphql"
})
class SentimentSubscriptionIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @BeforeEach
    void waitForKafkaListeners() {
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }
    }

    @Test
    void subscription_receivesSentimentEventPublishedToKafka() {
        WebSocketGraphQlTester tester = WebSocketGraphQlTester.builder(
                "ws://localhost:" + port + "/graphql",
                new ReactorNettyWebSocketClient()).build();

        Flux<SentimentAnalysisEvent> subscription = tester.document("""
                        subscription($streamer: String!) {
                          onSentiment(streamer: $streamer) {
                            sentimentEventId
                            sourceEventId
                            streamer
                            label
                            score
                          }
                        }
                        """)
                .variable("streamer", "test")
                .executeSubscription()
                .toFlux("onSentiment", SentimentAnalysisEvent.class);

        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId("sent-123");
        event.setSourceEventId("evt-123");
        event.setStreamer("test");
        event.setUser("u1");
        event.setMessage("great stream");
        event.setChatTimestamp(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setLabel("POSITIVE");
        event.setScore(0.82d);
        event.setModelVersion("stub-v1");

        StepVerifier.create(subscription)
                .then(() -> {
                    try {
                        testKafkaTemplate().send("stream.sentiment.events", "test", event).get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .assertNext(received -> {
                    org.assertj.core.api.Assertions.assertThat(received.getSentimentEventId()).isEqualTo("sent-123");
                    org.assertj.core.api.Assertions.assertThat(received.getSourceEventId()).isEqualTo("evt-123");
                    org.assertj.core.api.Assertions.assertThat(received.getStreamer()).isEqualTo("test");
                    org.assertj.core.api.Assertions.assertThat(received.getLabel()).isEqualTo("POSITIVE");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    private KafkaTemplate<String, SentimentAnalysisEvent> testKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
