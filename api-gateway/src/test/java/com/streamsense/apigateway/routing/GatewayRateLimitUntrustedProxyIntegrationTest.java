package com.streamsense.apigateway.routing;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * With no trusted proxy hops configured (the default), the client key is the socket address and X-Forwarded-For
 * is ignored, so rotating the header cannot open a fresh bucket per request.
 */
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
            "spring.kafka.consumer.group-id=api-gateway-test-group-untrusted-proxy",
            "streamsense.services.sentiment-service.base-url=http://localhost:8083",
            "streamsense.services.video-service.base-url=http://localhost:8084",
            "streamsense.gateway.trusted-proxy-hops=0",
            "streamsense.gateway.rate-limits[0].id=chat-ingest",
            "streamsense.gateway.rate-limits[0].path=/api/chat/ingest",
            "streamsense.gateway.rate-limits[0].method=POST",
            "streamsense.gateway.rate-limits[0].requests=2",
            "streamsense.gateway.rate-limits[0].window-seconds=60"
        })
class GatewayRateLimitUntrustedProxyIntegrationTest {

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
    void rotatingForwardedForDoesNotBypassTheLimit() {
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-1\"}"));
        CHAT_SERVICE.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("{\"eventId\":\"evt-2\"}"));

        postIngest("203.0.113.1").expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Remaining", "1");
        postIngest("203.0.113.2").expectStatus().isOk().expectHeader().valueEquals("X-RateLimit-Remaining", "0");
        postIngest("203.0.113.3")
                .expectStatus()
                .isEqualTo(429)
                .expectBody()
                .jsonPath("$.error")
                .isEqualTo("rate_limited");
    }

    private WebTestClient.ResponseSpec postIngest(String forwardedFor) {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build()
                .post()
                .uri("/api/chat/ingest")
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"streamer\":\"test\",\"user\":\"u1\",\"message\":\"hello\",\"timestamp\":1710000000000}")
                .exchange();
    }
}
