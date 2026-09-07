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
            "streamsense.services.sentiment-service.base-url=http://localhost:8083"
        })
class SponsorDetectionsQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "streamsense.services.video-service.base-url",
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
    void sponsorDetectionsQuery_returnsHistoryFromVideoService() {
        MOCK_WEB_SERVER.enqueue(
                new MockResponse()
                        .addHeader("Content-Type", "application/json")
                        .setBody(
                                """
                        [
                          {
                            "detectionEventId": "det-1",
                            "sourceFrameId": "frame-1",
                            "streamer": "test",
                            "frameRef": "frames/test.png",
                            "frameSequence": 1,
                            "capturedAt": 1710000000000,
                            "processedAt": 1710000000500,
                            "sponsor": "Nike",
                            "confidence": 0.91,
                            "modelVersion": "stub-v1",
                            "x": 0.12,
                            "y": 0.18,
                            "width": 0.31,
                            "height": 0.24
                          }
                        ]
                        """));

        graphQlTester
                .document(
                        """
                        query SponsorDetections($streamer: String!, $limit: Int!) {
                          sponsorDetections(streamer: $streamer, limit: $limit) {
                            detectionEventId
                            sponsor
                            confidence
                          }
                        }
                        """)
                .variable("streamer", "test")
                .variable("limit", 10)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isEmpty())
                .path("sponsorDetections[0].detectionEventId")
                .entity(String.class)
                .isEqualTo("det-1");
    }
}
