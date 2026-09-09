package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
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
            "streamsense.services.video-service.base-url=http://localhost:8084"
        })
class SessionsQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    private static final String SESSION_JSON =
            """
            {"id": 7, "streamer": "redbull-testing", "source": "CAPTURE", "twitchStreamId": "2750461300",
             "streamSessionId": "redbull-testing-2750461300", "channelLogin": "redbull-testing",
             "title": null, "category": null, "startedAt": 1788816420000, "endedAt": 1788824460000,
             "live": false, "durationMs": 8040000, "peakViewers": null, "averageViewers": null, "viewerSamples": 0}
            """;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "streamsense.services.analytics-service.base-url",
                () -> MOCK_WEB_SERVER.url("/").toString());
    }

    @BeforeAll
    static void startServer() throws Exception {
        MOCK_WEB_SERVER.start();
    }

    @AfterAll
    static void shutdownServer() throws Exception {
        MOCK_WEB_SERVER.shutdown();
    }

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Test
    void sessionsQueryPassesTheRangeThroughAndMapsTheSession() throws Exception {
        MOCK_WEB_SERVER.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("[" + SESSION_JSON + "]"));

        graphQlTester
                .document(
                        """
                        query {
                          sessions(streamer: "redbull-testing", from: 1788800000000, to: 1788900000000, limit: 5) {
                            id streamer source twitchStreamId startedAt endedAt live durationMs peakViewers viewerSamples
                          }
                        }
                        """)
                .execute()
                .path("sessions[0].id")
                .entity(String.class)
                .isEqualTo("7")
                .path("sessions[0].source")
                .entity(String.class)
                .isEqualTo("CAPTURE")
                .path("sessions[0].live")
                .entity(Boolean.class)
                .isEqualTo(false)
                .path("sessions[0].durationMs")
                .entity(Double.class)
                .isEqualTo(8040000.0d);

        RecordedRequest request = MOCK_WEB_SERVER.takeRequest();
        assertThat(request.getPath())
                .isEqualTo(
                        "/api/analytics/streams/redbull-testing/sessions?from=1788800000000&to=1788900000000&limit=5");
    }

    @Test
    void sessionQueryReturnsNullForUnknownOrMalformedIds() {
        int requestsBefore = MOCK_WEB_SERVER.getRequestCount();
        MOCK_WEB_SERVER.enqueue(new MockResponse().setResponseCode(404));

        graphQlTester
                .document("query { session(id: \"999\") { id } }")
                .execute()
                .path("session")
                .valueIsNull();

        // A malformed id never reaches the analytics service.
        graphQlTester
                .document("query { session(id: \"not-a-number\") { id } }")
                .execute()
                .path("session")
                .valueIsNull();
        assertThat(MOCK_WEB_SERVER.getRequestCount() - requestsBefore).isEqualTo(1);
    }
}
