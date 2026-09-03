package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Downstream failures surface as GraphQL errors with a stable extensions.code, never as an opaque INTERNAL_ERROR. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.topics.transcriptSegments=stream.transcript.segments",
        "streamsense.topics.transcriptSentimentEvents=stream.transcript.sentiment.events",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=api-gateway-test-group",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "streamsense.services.video-service.base-url=http://localhost:8084",
        // A port nothing listens on: connection refused, which is what an outage looks like.
        "streamsense.services.analytics-service.base-url=http://127.0.0.1:1"
})
class GraphQlErrorAdviceTest {

    private static final MockWebServer RECOMMENDATION_SERVICE = new MockWebServer();

    private static final String RECOMMENDATIONS_QUERY = """
            query Recommendations($streamer: String!, $limit: Int!) {
              recommendations(streamer: $streamer, limit: $limit) {
                recommendationId
              }
            }
            """;

    private static final String SUMMARY_QUERY = """
            query Summary($streamer: String!) {
              streamMetricsSummary(streamer: $streamer, windowMinutes: 15) {
                streamer
              }
            }
            """;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsense.services.recommendation-service.base-url", () -> RECOMMENDATION_SERVICE.url("/").toString());
    }

    @BeforeAll
    static void startServer() throws Exception {
        RECOMMENDATION_SERVICE.start();
    }

    @AfterAll
    static void shutdownServer() throws Exception {
        RECOMMENDATION_SERVICE.shutdown();
    }

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Test
    void downstreamErrorStatusIsReportedWithCodeAndStatus() {
        RECOMMENDATION_SERVICE.enqueue(new MockResponse().setResponseCode(503).setBody("upstream is drowning"));

        graphQlTester.document(RECOMMENDATIONS_QUERY)
                .variable("streamer", "test")
                .variable("limit", 3)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getExtensions()).containsEntry("code", "DOWNSTREAM_ERROR").containsEntry("status", 503);
                    assertThat(errors.get(0).getMessage()).isEqualTo("Downstream service returned an error");
                    assertThat(errors.get(0).getMessage()).doesNotContain("drowning");
                    assertThat(errors.get(0).getPath()).isEqualTo("recommendations");
                });
    }

    @Test
    void downstreamValidationFailureIsTheCallersMistake() {
        RECOMMENDATION_SERVICE.enqueue(new MockResponse().setResponseCode(400)
                .addHeader("Content-Type", "application/problem+json")
                .setBody("{\"type\":\"https://streamsense.dev/problems/validation-failed\",\"status\":400}"));

        graphQlTester.document(RECOMMENDATIONS_QUERY)
                .variable("streamer", "test")
                .variable("limit", 3)
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getExtensions()).containsEntry("code", "BAD_REQUEST").containsEntry("status", 400);
                    assertThat(errors.get(0).getErrorType()).hasToString("BAD_REQUEST");
                    assertThat(errors.get(0).getMessage()).isEqualTo("Downstream service rejected the request");
                });
    }

    @Test
    void unreachableDownstreamIsReportedAsUnavailable() {
        graphQlTester.document(SUMMARY_QUERY)
                .variable("streamer", "test")
                .execute()
                .errors()
                .satisfy(errors -> {
                    assertThat(errors).hasSize(1);
                    assertThat(errors.get(0).getExtensions()).containsEntry("code", "DOWNSTREAM_UNAVAILABLE").containsEntry("host", "127.0.0.1");
                    assertThat(errors.get(0).getMessage()).isEqualTo("Downstream service unavailable");
                    assertThat(errors.get(0).getPath()).isEqualTo("streamMetricsSummary");
                });
    }
}
