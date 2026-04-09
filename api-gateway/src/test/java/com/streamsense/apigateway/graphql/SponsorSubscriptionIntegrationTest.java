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

import com.streamsense.apigateway.events.SponsorDetectionEvent;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EmbeddedKafka(partitions = 1, topics = { "stream.sponsor.detections" })
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=api-gateway-test-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "streamsense.services.video-service.base-url=http://localhost:8084",
        "spring.graphql.websocket.path=/graphql"
})
class SponsorSubscriptionIntegrationTest {

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
    void subscription_receivesSponsorDetectionPublishedToKafka() {
        WebSocketGraphQlTester tester = WebSocketGraphQlTester.builder(
                "ws://localhost:" + port + "/graphql",
                new ReactorNettyWebSocketClient()).build();

        Flux<SponsorDetectionEvent> subscription = tester.document("""
                        subscription($streamer: String!) {
                          onSponsorDetection(streamer: $streamer) {
                            detectionEventId
                            sourceFrameId
                            streamer
                            sponsor
                            confidence
                          }
                        }
                        """)
                .variable("streamer", "test")
                .executeSubscription()
                .toFlux("onSponsorDetection", SponsorDetectionEvent.class);

        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId("det-123");
        event.setSourceFrameId("frame-123");
        event.setStreamer("test");
        event.setFrameRef("frames/test.png");
        event.setFrameSequence(1L);
        event.setCapturedAt(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setSponsor("Nike");
        event.setConfidence(0.91d);
        event.setModelVersion("stub-v1");
        event.setX(0.12d);
        event.setY(0.18d);
        event.setWidth(0.31d);
        event.setHeight(0.24d);

        StepVerifier.create(subscription)
                .then(() -> {
                    try {
                        testKafkaTemplate().send("stream.sponsor.detections", "test", event).get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .assertNext(received -> {
                    org.assertj.core.api.Assertions.assertThat(received.getDetectionEventId()).isEqualTo("det-123");
                    org.assertj.core.api.Assertions.assertThat(received.getSourceFrameId()).isEqualTo("frame-123");
                    org.assertj.core.api.Assertions.assertThat(received.getStreamer()).isEqualTo("test");
                    org.assertj.core.api.Assertions.assertThat(received.getSponsor()).isEqualTo("Nike");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }

    private KafkaTemplate<String, SponsorDetectionEvent> testKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
