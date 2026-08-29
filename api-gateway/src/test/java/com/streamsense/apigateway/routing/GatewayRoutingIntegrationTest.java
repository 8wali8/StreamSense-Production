package com.streamsense.apigateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events",
            "streamsense.topics.sponsorDetections=stream.sponsor.detections",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group",
            "streamsense.services.sentiment-service.base-url=http://localhost:8083",
            "streamsense.services.video-service.base-url=http://localhost:8084"
        })
class GatewayRoutingIntegrationTest {

    private static final MockWebServer CHAT_SERVICE = new MockWebServer();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].id", () -> "chat-service-api");
        registry.add(
                "spring.cloud.gateway.routes[0].uri",
                () -> CHAT_SERVICE.url("/").toString());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/chat/**");
    }

    @BeforeAll
    static void startServer() throws Exception {
        CHAT_SERVICE.start();
    }

    @AfterAll
    static void shutdownServer() throws Exception {
        CHAT_SERVICE.shutdown();
    }

    @LocalServerPort
    int port;

    @Test
    void proxiesChatIngestThroughCentralGatewayRoute() throws Exception {
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-123\"}"));

        webTestClient()
                .post()
                .uri("/api/chat/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"streamer\":\"test\",\"user\":\"u1\",\"message\":\"hello\",\"timestamp\":1710000000000}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-StreamSense-Route-Id", "chat-service-api")
                .expectBody()
                .jsonPath("$.eventId")
                .isEqualTo("evt-123");

        RecordedRequest recordedRequest = CHAT_SERVICE.takeRequest();
        assertThat(recordedRequest.getPath()).isEqualTo("/api/chat/ingest");
        assertThat(recordedRequest.getHeader("X-StreamSense-Route-Id")).isEqualTo("chat-service-api");
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
