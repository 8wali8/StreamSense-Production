package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.support.TestJwtTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * An access link is a token without a role: it reads any channel, like an operator, but it cannot
 * steer the pipeline. Only a Twitch sign-in on the operators list can.
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
            "spring.kafka.consumer.group-id=api-gateway-test-group",
            "streamsense.gateway.auth.enabled=true",
            "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
        })
class AccessLinkScopeIntegrationTest {

    private static final String ACCESS_LINK = TestJwtTokens.validToken("demo-viewer");
    private static final String OPERATOR = TestJwtTokens.tokenWithRole("ops", "operator");

    @LocalServerPort
    int port;

    @Test
    void anAccessLinkCannotSteerThePipeline() {
        client().post()
                .uri("/api/chat/twitch/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_LINK)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"channels\":[\"ninja\"]}")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("operator_required");
    }

    @Test
    void anAccessLinkStillReadsAnyChannel() {
        // The gate lets both through to the proxy, which has no upstream here: anything but 401 or 403.
        assertThat(status("/api/analytics/streams/pokimane/sessions", ACCESS_LINK))
                .isNotIn(401, 403);
        assertThat(status("/api/chat/twitch/status", ACCESS_LINK)).isNotIn(401, 403);

        client().post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_LINK)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"{ deals(streamer: \\\"pokimane\\\") { id } }\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                // Analytics is not running: a downstream error, not a refusal of the channel.
                .jsonPath("$.errors[0].extensions.code")
                .value(org.hamcrest.Matchers.not(ChannelScopeInterceptor.CODE));
    }

    @Test
    void anAccessLinkCannotStartMeasurementOfAChannelItDoesNotOwn() {
        // The self-service paths are carved out of the operator-only ones for a streamer acting on their own
        // channel. An access link names no channel, so a viewer link must not be enough to start capture.
        client().put()
                .uri("/api/chat/twitch/channels/ninja")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_LINK)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("operator_required");

        // Reading whether a channel is measured is an ordinary read, and an access link reads any channel.
        assertThat(status("/api/chat/twitch/channels/ninja", ACCESS_LINK)).isNotIn(401, 403);
    }

    @Test
    void anOperatorSignInSteersThePipeline() {
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

    private int status(String path, String token) {
        return client().get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .returnResult(Void.class)
                .getStatus()
                .value();
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
