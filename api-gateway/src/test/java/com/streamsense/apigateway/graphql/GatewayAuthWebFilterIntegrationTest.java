package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.support.TestJwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events",
            "streamsense.topics.sponsorDetections=stream.sponsor.detections",
            "streamsense.services.sentiment-service.base-url=http://localhost:8083",
            "streamsense.services.video-service.base-url=http://localhost:8084",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group",
            "streamsense.gateway.auth.enabled=true",
            "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
        })
class GatewayAuthWebFilterIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void rejectsGraphqlRequestsWithoutBearerToken() {
        webTestClient()
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("missing_bearer_token");
    }

    @Test
    void rejectsMalformedTokens() {
        webTestClient()
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.malformedToken())
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("invalid_jwt_shape");
    }

    @Test
    void rejectsExpiredTokens() {
        webTestClient()
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.expiredToken("demo-user"))
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("token_expired");
    }

    @Test
    void allowsValidTokensAndExposesAuthSubjectHeader() {
        webTestClient()
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwtTokens.validToken("demo-user"))
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-StreamSense-Auth-Subject", "demo-user")
                .expectBody()
                .jsonPath("$.data.health")
                .isEqualTo("ok");
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        String forged = TestJwtTokens.tokenSignedWith(
                "another-secret-that-is-at-least-32-bytes-long",
                "demo-user",
                "streamsense-local",
                java.util.List.of("streamsense-clients"),
                java.time.Instant.now().plusSeconds(600).getEpochSecond(),
                null,
                "HS256");

        webTestClient()
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + forged)
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("invalid_jwt_signature");
    }

    @Test
    void doesNotTreatASpoofedUpgradeHeaderOnAPostAsAWebSocketHandshake() {
        webTestClient()
                .post()
                .uri("/graphql")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.UPGRADE, "websocket")
                .header(HttpHeaders.CONNECTION, "Upgrade")
                .header("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
                .header("Sec-WebSocket-Version", "13")
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("missing_bearer_token");
    }

    @Test
    void keepsActuatorHealthUnauthenticated() {
        webTestClient()
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
