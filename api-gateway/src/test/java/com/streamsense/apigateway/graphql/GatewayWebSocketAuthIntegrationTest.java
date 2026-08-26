package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import com.streamsense.apigateway.support.GraphqlTransportWsProbe;
import com.streamsense.apigateway.support.GraphqlTransportWsProbe.Session;
import com.streamsense.apigateway.support.TestJwtTokens;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "streamsense.services.video-service.base-url=http://localhost:8084",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=api-gateway-test-group-ws-auth",
        "spring.graphql.websocket.path=/graphql",
        "streamsense.gateway.auth.enabled=true",
        "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
})
class GatewayWebSocketAuthIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @LocalServerPort
    int port;

    @Test
    void acceptsConnectionInitCarryingAValidBearerToken() {
        Session session = GraphqlTransportWsProbe.connect(port,
                Map.of("Authorization", "Bearer " + TestJwtTokens.validToken("demo-user")), true, TIMEOUT);

        assertThat(session.acknowledged()).isTrue();
        assertThat(session.messages()).anySatisfy(message -> assertThat(message).contains("\"health\":\"ok\""));
    }

    @Test
    void closesTheSocketWhenConnectionInitHasNoToken() {
        Session session = GraphqlTransportWsProbe.connect(port, Map.of(), false, TIMEOUT);

        assertThat(session.acknowledged()).isFalse();
        assertThat(session.closeStatus()).isNotNull();
        assertThat(session.closeStatus().getCode()).isEqualTo(4401);
    }

    @Test
    void closesTheSocketWhenTheTokenIsSignedWithAnotherKey() {
        String forged = TestJwtTokens.tokenSignedWith("another-secret-that-is-at-least-32-bytes-long", "demo-user",
                "streamsense-local", List.of("streamsense-clients"),
                Instant.now().plusSeconds(600).getEpochSecond(), null, "HS256");

        Session session = GraphqlTransportWsProbe.connect(port, Map.of("Authorization", "Bearer " + forged), false,
                TIMEOUT);

        assertThat(session.acknowledged()).isFalse();
        assertThat(session.closeStatus().getCode()).isEqualTo(4401);
    }

    @Test
    void closesAnAcknowledgedSocketOnceItsTokenExpires() {
        String shortLived = TestJwtTokens.token("demo-user", "streamsense-local", List.of("streamsense-clients"),
                Instant.now().plusSeconds(2).getEpochSecond(), null, "HS256");

        // No query and no client-side close: the session only ends if the server ends it at exp.
        Session session = GraphqlTransportWsProbe.connect(port, Map.of("Authorization", "Bearer " + shortLived), false,
                TIMEOUT);

        assertThat(session.acknowledged()).isTrue();
        assertThat(session.closeStatus()).isNotNull();
        assertThat(session.closeStatus().getCode()).isEqualTo(4401);
        assertThat(session.closeStatus().getReason()).isEqualTo("token_expired");
    }
}
