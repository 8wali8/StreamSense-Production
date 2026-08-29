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
            "streamsense.services.sentiment-service.base-url=http://localhost:8083",
            "streamsense.services.video-service.base-url=http://localhost:8084",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group"
        })
class RecommendationsQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "streamsense.services.recommendation-service.base-url",
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
    void recommendationsQueryReturnsRecommendationServiceResults() {
        MOCK_WEB_SERVER.enqueue(
                new MockResponse()
                        .addHeader("Content-Type", "application/json")
                        .setBody(
                                """
                        [
                          {
                            "recommendationId": "test:sponsor_alignment",
                            "streamer": "test",
                            "title": "Highlight Nike moments while they are landing",
                            "category": "SPONSOR_ALIGNMENT",
                            "score": 0.83,
                            "reasonSummary": "Nike is the most visible sponsor in the recent window.",
                            "reasons": [
                              "Nike appeared in 67% of recent sponsor detections.",
                              "Average confidence for Nike was 0.88."
                            ],
                            "experimentName": "recommendation-ranking-v1",
                            "variantId": "balanced",
                            "generatedAt": 1712890800000
                          }
                        ]
                        """));

        graphQlTester
                .document(
                        """
                        query Recommendations($streamer: String!, $limit: Int!) {
                          recommendations(streamer: $streamer, limit: $limit) {
                            recommendationId
                            category
                            score
                            reasonSummary
                            reasons
                            variantId
                          }
                        }
                        """)
                .variable("streamer", "test")
                .variable("limit", 3)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isEmpty())
                .path("recommendations[0].category")
                .entity(String.class)
                .isEqualTo("SPONSOR_ALIGNMENT");
    }
}
