package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.support.GraphqlTransportWsProbe;
import com.streamsense.apigateway.support.GraphqlTransportWsProbe.Session;
import com.streamsense.apigateway.support.TestJwtTokens;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Every token the gateway accepts carries a role, operator or streamer. The access links of old were
 * tokens without one; since they were retired such a token is refused at the door with
 * {@code missing_role}, on HTTP and on the subscription socket alike, however well it is signed.
 */
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
            "spring.kafka.consumer.group-id=api-gateway-test-group-role-claim",
            "spring.graphql.websocket.path=/graphql",
            "streamsense.gateway.auth.enabled=true",
            "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
        })
class RoleClaimIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String NO_ROLE = TestJwtTokens.tokenWithoutRole("demo-viewer");
    private static final String OPERATOR = TestJwtTokens.tokenWithRole("ops", "operator");

    @LocalServerPort
    int port;

    @Test
    void aTokenWithoutARoleIsRefusedEverywhere() {
        // A GraphQL read, a REST read, and a pipeline write: 401 missing_role before any of them is looked at.
        for (String path : new String[] {"/api/analytics/streams/pokimane/sessions", "/api/chat/twitch/status"}) {
            client().get()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + NO_ROLE)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectBody()
                    .jsonPath("$.reason")
                    .isEqualTo("missing_role");
        }
        client().post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + NO_ROLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectHeader()
                .valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("missing_role");
        client().post()
                .uri("/api/chat/twitch/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + NO_ROLE)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"channels\":[\"ninja\"]}")
                .exchange()
                .expectStatus()
                .isUnauthorized()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("missing_role");
    }

    @Test
    void aTokenWithoutARoleCannotOpenASubscriptionSocket() {
        Session session =
                GraphqlTransportWsProbe.connect(port, Map.of("Authorization", "Bearer " + NO_ROLE), false, TIMEOUT);

        assertThat(session.acknowledged()).isFalse();
        assertThat(session.closeStatus()).isNotNull();
        assertThat(session.closeStatus().getCode()).isEqualTo(4401);
    }

    @Test
    void anOperatorSignInReadsAnyChannelAndSteersThePipeline() {
        // The gate lets both through to the proxy, which has no upstream here: anything but 401 or 403.
        assertThat(client().get()
                        .uri("/api/analytics/streams/pokimane/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OPERATOR)
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus()
                        .value())
                .isNotIn(401, 403);
        assertThat(client().post()
                        .uri("/api/chat/twitch/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OPERATOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"channels\":[\"ninja\"]}")
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus()
                        .value())
                .isNotIn(401, 403);
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
