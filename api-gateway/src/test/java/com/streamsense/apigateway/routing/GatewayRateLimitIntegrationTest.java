package com.streamsense.apigateway.routing;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
            "streamsense.services.video-service.base-url=http://localhost:8084",
            "streamsense.gateway.trusted-proxy-hops=1",
            "streamsense.gateway.rate-limits[0].id=chat-ingest",
            "streamsense.gateway.rate-limits[0].path=/api/chat/ingest",
            "streamsense.gateway.rate-limits[0].method=POST",
            "streamsense.gateway.rate-limits[0].requests=2",
            "streamsense.gateway.rate-limits[0].window-seconds=60"
        })
class GatewayRateLimitIntegrationTest {

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

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void rejectsRequestsAfterConfiguredBurstLimit() {
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-1\"}"));
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-2\"}"));

        postIngest("203.0.113.10").expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Remaining", "1");
        postIngest("203.0.113.10").expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Remaining", "0");
        postIngest("203.0.113.10")
                .expectStatus()
                .isEqualTo(429)
                .expectHeader()
                .valueEquals("Retry-After", "60")
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("rate_limited");

        assertThat(meterRegistry
                        .get("streamsense_gateway_rate_limit_rejections_total")
                        .tag("limit", "chat-ingest")
                        .tag("path", "/api/chat/ingest")
                        .counter()
                        .count())
                .isEqualTo(1.0d);
    }

    @Test
    void tracksClientsSeparately() {
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-a\"}"));
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-b\"}"));

        postIngest("198.51.100.10").expectStatus().isOk();
        postIngest("198.51.100.11").expectStatus().isOk();
    }

    @Test
    void keysOnTheEntryAppendedByTheTrustedProxy() {
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-c\"}"));
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-d\"}"));
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-e\"}"));

        // The leftmost entry is client-supplied; only the rightmost one came from the trusted hop.
        postIngest("10.0.0.1, 203.0.113.50")
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-RateLimit-Remaining", "1");
        postIngest("10.0.0.2, 203.0.113.50")
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-RateLimit-Remaining", "0");
        postIngest("10.0.0.2, 203.0.113.51")
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-RateLimit-Remaining", "1");
    }

    private WebTestClient.ResponseSpec postIngest(String clientIp) {
        return webTestClient()
                .post()
                .uri("/api/chat/ingest")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"streamer\":\"test\",\"user\":\"u1\",\"message\":\"hello\",\"timestamp\":1710000000000}")
                .exchange();
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
