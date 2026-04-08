package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=api-gateway-test-group"
})
class SentimentHistoryQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsense.services.sentiment-service.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
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
    void recentSentimentQuery_returnsHistoryFromSentimentService() {
        MOCK_WEB_SERVER.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {
                            "sentimentEventId": "sent-1",
                            "sourceEventId": "src-1",
                            "streamer": "test",
                            "user": "u1",
                            "message": "great stream",
                            "chatTimestamp": 1710000000000,
                            "processedAt": 1710000000500,
                            "label": "POSITIVE",
                            "score": 0.82,
                            "modelVersion": "stub-v1"
                          }
                        ]
                        """));

        graphQlTester.document("""
                        query RecentSentiment($streamer: String!, $limit: Int!) {
                          recentSentiment(streamer: $streamer, limit: $limit) {
                            sentimentEventId
                            label
                            score
                          }
                        }
                        """)
                .variable("streamer", "test")
                .variable("limit", 10)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isEmpty())
                .path("recentSentiment[0].sentimentEventId")
                .entity(String.class)
                .isEqualTo("sent-1");
    }

    @Test
    void recentSentimentQuery_returnsFallbackHistoryWithoutErrors() {
        MOCK_WEB_SERVER.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {
                            "sentimentEventId": "sent-fallback",
                            "sourceEventId": "src-fallback",
                            "streamer": "test",
                            "user": "u2",
                            "message": "ml fallback case",
                            "chatTimestamp": 1710000002000,
                            "processedAt": 1710000002500,
                            "label": "NEUTRAL",
                            "score": 0.0,
                            "modelVersion": "fallback"
                          }
                        ]
                        """));

        graphQlTester.document("""
                        query RecentSentiment($streamer: String!, $limit: Int!) {
                          recentSentiment(streamer: $streamer, limit: $limit) {
                            sentimentEventId
                            label
                            modelVersion
                          }
                        }
                        """)
                .variable("streamer", "test")
                .variable("limit", 10)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isEmpty())
                .path("recentSentiment[0].label")
                .entity(String.class)
                .isEqualTo("NEUTRAL");
    }
}
