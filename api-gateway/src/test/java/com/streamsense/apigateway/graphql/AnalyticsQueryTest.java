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
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.topics.transcriptSegments=stream.transcript.segments",
        "streamsense.topics.transcriptSentimentEvents=stream.transcript.sentiment.events",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=api-gateway-test-group",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "streamsense.services.video-service.base-url=http://localhost:8084"
})
class AnalyticsQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsense.services.analytics-service.base-url", () -> MOCK_WEB_SERVER.url("/").toString());
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
    void streamMetricsSummaryQuery_returnsAnalyticsSummary() {
        MOCK_WEB_SERVER.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "streamer": "test",
                          "streamSessionId": "test-session",
                          "windowMinutes": 15,
                          "bucketSizeSeconds": 60,
                          "windowStart": 1710000000000,
                          "windowEnd": 1710000900000,
                          "chat": {
                            "totalMessages": 42,
                            "messagesPerMinute": 2.8,
                            "uniqueChatters": 11,
                            "peakMessagesPerMinute": 9
                          },
                          "chatSentiment": {
                            "positive": 12,
                            "neutral": 20,
                            "negative": 10,
                            "averageScore": 0.12,
                            "negativeRatio": 0.238
                          },
                          "transcriptSentiment": {
                            "positive": 2,
                            "neutral": 4,
                            "negative": 1,
                            "averageScore": 0.3,
                            "negativeRatio": 0.143
                          },
                          "sponsorExposure": {
                            "totalDetections": 3,
                            "acceptedDetections": 3,
                            "estimatedExposureMs": 30000,
                            "topSponsors": [
                              {
                                "sponsor": "Nike",
                                "detectionCount": 3,
                                "acceptedDetectionCount": 3,
                                "estimatedExposureMs": 30000,
                                "averageConfidence": 0.81,
                                "maxConfidence": 0.9,
                                "fallbackDetectionCount": 0,
                                "lowConfidenceDetectionCount": 0
                              }
                            ]
                          },
                          "engagement": {
                            "spikeCount": 1,
                            "latestSpikeAt": 1710000600000
                          },
                          "risk": {
                            "level": "LOW",
                            "score": 0.2,
                            "factors": [
                              {"name": "chatNegativeRatio", "value": 0.238, "weight": 0.35}
                            ]
                          },
                          "dataQuality": {
                            "lowData": false,
                            "latestEventAt": 1710000600000,
                            "aggregationLagMs": 1500
                          }
                        }
                        """));

        graphQlTester.document("""
                        query Metrics($streamer: String!, $windowMinutes: Int!) {
                          streamMetricsSummary(streamer: $streamer, windowMinutes: $windowMinutes) {
                            chat { totalMessages uniqueChatters }
                            sponsorExposure { topSponsors { sponsor estimatedExposureMs } }
                            risk { level score factors { name value weight } }
                          }
                        }
                        """)
                .variable("streamer", "test")
                .variable("windowMinutes", 15)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isEmpty())
                .path("streamMetricsSummary.chat.totalMessages")
                .entity(Double.class)
                .isEqualTo(42.0d);
    }

    @Test
    void streamMetricsTimeseriesQuery_returnsBuckets() {
        MOCK_WEB_SERVER.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {
                            "bucketStart": 1710000000000,
                            "bucketEnd": 1710000060000,
                            "chatMessageCount": 7,
                            "uniqueChatters": 3,
                            "chatAverageScore": -0.2,
                            "chatNegativeRatio": 0.5,
                            "transcriptAverageScore": 0.1,
                            "transcriptNegativeRatio": 0.0,
                            "sponsorDetectionCount": 1,
                            "estimatedSponsorExposureMs": 10000,
                            "engagementSpike": true,
                            "negativeSpike": false
                          }
                        ]
                        """));

        graphQlTester.document("""
                        query Series($streamer: String!, $windowMinutes: Int!, $bucketSeconds: Int!) {
                          streamMetricsTimeseries(streamer: $streamer, windowMinutes: $windowMinutes, bucketSeconds: $bucketSeconds) {
                            bucketStart
                            chatMessageCount
                            engagementSpike
                          }
                        }
                        """)
                .variable("streamer", "test")
                .variable("windowMinutes", 15)
                .variable("bucketSeconds", 60)
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).isEmpty())
                .path("streamMetricsTimeseries[0].engagementSpike")
                .entity(Boolean.class)
                .isEqualTo(true);
    }
}
