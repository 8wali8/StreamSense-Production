package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.support.TestJwtTokens;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** A streamer signed in with Twitch sees their own channel and nothing else, over GraphQL and REST alike. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events",
            "streamsense.topics.sponsorDetections=stream.sponsor.detections",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group",
            "streamsense.gateway.auth.enabled=true",
            "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
        })
class ChannelScopeIntegrationTest {

    private static final MockWebServer ANALYTICS = new MockWebServer();
    private static final String STREAMER = TestJwtTokens.tokenWithRole("ninja", "streamer");
    private static final String OPERATOR = TestJwtTokens.tokenWithRole("ops", "operator");

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void analytics(DynamicPropertyRegistry registry) throws IOException {
        ANALYTICS.start();
        registry.add(
                "streamsense.services.analytics-service.base-url",
                () -> ANALYTICS.url("/").toString());
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "analytics-service-api");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri",
                () -> ANALYTICS.url("/").toString());
        registry.add("spring.cloud.gateway.server.webflux.routes[0].predicates[0]", () -> "Path=/api/analytics/**");
    }

    @AfterAll
    static void stop() throws IOException {
        ANALYTICS.shutdown();
    }

    @Test
    void aStreamerMayQueryTheirOwnChannelButNotAnother() throws Exception {
        // Another channel by literal, and by variable: refused before any resolver runs.
        graphql(STREAMER, "{ recentSentiment(streamer: \"pokimane\", limit: 5) { eventId } }", null)
                .jsonPath("$.errors[0].extensions.code")
                .isEqualTo("CHANNEL_FORBIDDEN")
                .jsonPath("$.data")
                .doesNotExist();
        graphql(STREAMER, "query($s: String!) { deals(streamer: $s) { id } }", "{\"s\":\"pokimane\"}")
                .jsonPath("$.errors[0].extensions.code")
                .isEqualTo("CHANNEL_FORBIDDEN");

        // Their own channel reaches analytics, which is told who asked.
        ANALYTICS.enqueue(json("[]"));
        graphql(STREAMER, "{ deals(streamer: \"Ninja\") { id } }", null)
                .jsonPath("$.errors")
                .doesNotExist()
                .jsonPath("$.data.deals")
                .isArray();
        RecordedRequest deals = ANALYTICS.takeRequest(5, TimeUnit.SECONDS);
        assertThat(deals.getPath()).startsWith("/api/analytics/deals");

        // An operator is not confined.
        ANALYTICS.enqueue(json("[]"));
        graphql(OPERATOR, "{ deals(streamer: \"pokimane\") { id } }", null)
                .jsonPath("$.errors")
                .doesNotExist();
        ANALYTICS.takeRequest(5, TimeUnit.SECONDS);
    }

    @Test
    void aDealOrSessionAddressedByIdIsCheckedAgainstItsOwner() throws Exception {
        ANALYTICS.enqueue(json(deal(7, "pokimane")));
        graphql(STREAMER, "{ deal(id: \"7\") { id streamer } }", null)
                .jsonPath("$.errors[0].extensions.code")
                .isEqualTo("CHANNEL_FORBIDDEN");
        ANALYTICS.takeRequest(5, TimeUnit.SECONDS);

        ANALYTICS.enqueue(json(deal(8, "ninja")));
        graphql(STREAMER, "{ deal(id: \"8\") { id streamer } }", null)
                .jsonPath("$.errors")
                .doesNotExist()
                .jsonPath("$.data.deal.streamer")
                .isEqualTo("ninja");
        ANALYTICS.takeRequest(5, TimeUnit.SECONDS);

        ANALYTICS.enqueue(json(session(21, "pokimane")));
        graphql(STREAMER, "{ session(id: \"21\") { id streamer } }", null)
                .jsonPath("$.errors[0].extensions.code")
                .isEqualTo("CHANNEL_FORBIDDEN");
        ANALYTICS.takeRequest(5, TimeUnit.SECONDS);
    }

    @Test
    void restRoutesNamingAChannelAreRefusedAtTheGateAndTheRestCarryTheScopeHeaders() throws Exception {
        client().get()
                .uri("/api/analytics/streams/pokimane/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STREAMER)
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("channel_forbidden");
        client().get()
                .uri("/api/analytics/deals?streamer=pokimane")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STREAMER)
                .exchange()
                .expectStatus()
                .isForbidden();

        // By id the gateway cannot know the owner; analytics-service decides from the headers it forwards,
        // and a client cannot smuggle its own.
        ANALYTICS.enqueue(json(deal(8, "ninja")));
        client().get()
                .uri("/api/analytics/deals/8")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + STREAMER)
                .header("X-StreamSense-Auth-Role", "operator")
                .header("X-StreamSense-Auth-Login", "someone-else")
                .exchange()
                .expectStatus()
                .isOk();
        RecordedRequest forwarded = ANALYTICS.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded.getPath()).isEqualTo("/api/analytics/deals/8");
        assertThat(forwarded.getHeader("X-StreamSense-Auth-Login")).isEqualTo("ninja");
        assertThat(forwarded.getHeader("X-StreamSense-Auth-Role")).isEqualTo("streamer");
        assertThat(forwarded.getHeaders().values("X-StreamSense-Auth-Role")).hasSize(1);
    }

    private WebTestClient.BodyContentSpec graphql(String token, String query, String variables) {
        String body = variables == null
                ? "{\"query\":" + quote(query) + "}"
                : "{\"query\":" + quote(query) + ",\"variables\":" + variables + "}";
        return client().post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody();
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String deal(long id, String streamer) {
        return "{\"id\":" + id + ",\"streamer\":\"" + streamer
                + "\",\"sponsor\":\"Red Bull\",\"startsAt\":1788000000000,"
                + "\"endsAt\":null,\"promisedStreams\":null,\"fee\":null,\"currency\":\"USD\",\"cpmPer30sEquivalent\":10.0,"
                + "\"hostReadRatePer1000\":20.0,\"trackedLink\":null,\"chatCommand\":null,\"channelPointReward\":null,"
                + "\"createdAt\":1788000000000,\"status\":\"ACTIVE\"}";
    }

    private static String session(long id, String streamer) {
        return "{\"id\":" + id + ",\"streamer\":\"" + streamer + "\",\"source\":\"HELIX\",\"twitchStreamId\":\"1\","
                + "\"streamSessionId\":null,\"channelLogin\":\"" + streamer + "\",\"title\":\"t\",\"category\":null,"
                + "\"startedAt\":1788000000000,\"endedAt\":null,\"live\":true,\"durationMs\":1000,\"peakViewers\":null,"
                + "\"averageViewers\":null,\"viewerSamples\":0,\"vodId\":null}";
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
