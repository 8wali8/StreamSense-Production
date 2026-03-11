package com.streamsense.apigateway.graphql;

import java.time.Duration;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.streamsense.apigateway.events.ChatMessageEvent;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = { "stream.chat.messages" })
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "spring.graphql.websocket.path=/graphql"
})
class ChatSubscriptionIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void subscription_receivesEventPublishedToKafka() {
        WebSocketGraphQlTester tester = WebSocketGraphQlTester.builder(
                "ws://localhost:" + port + "/graphql",
                new ReactorNettyWebSocketClient()).build();

        Flux<ChatMessageEvent> subscription = tester.document("""
                subscription($streamer: String!) {
                  onChatMessage(streamer: $streamer) {
                    eventId
                    streamer
                    user
                    message
                    timestamp
                  }
                }
                """)
                .variable("streamer", "test")
                .executeSubscription()
                .toFlux("onChatMessage", ChatMessageEvent.class);

        KafkaTemplate<String, ChatMessageEvent> kafkaTemplate = testKafkaTemplate();

        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-123");
        event.setStreamer("test");
        event.setUser("u1");
        event.setMessage("hello from kafka test");
        event.setTimestamp(1710000000000L);

        StepVerifier.create(subscription)
                .then(() -> kafkaTemplate.send("stream.chat.messages", "test", event))
                .assertNext(received -> {
                    org.assertj.core.api.Assertions.assertThat(received.getEventId()).isEqualTo("evt-123");
                    org.assertj.core.api.Assertions.assertThat(received.getStreamer()).isEqualTo("test");
                    org.assertj.core.api.Assertions.assertThat(received.getUser()).isEqualTo("u1");
                    org.assertj.core.api.Assertions.assertThat(received.getMessage())
                            .isEqualTo("hello from kafka test");
                    org.assertj.core.api.Assertions.assertThat(received.getTimestamp()).isEqualTo(1710000000000L);
                })
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    private KafkaTemplate<String, ChatMessageEvent> testKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        DefaultKafkaProducerFactory<String, ChatMessageEvent> factory = new DefaultKafkaProducerFactory<>(props);

        return new KafkaTemplate<>(factory);
    }
}