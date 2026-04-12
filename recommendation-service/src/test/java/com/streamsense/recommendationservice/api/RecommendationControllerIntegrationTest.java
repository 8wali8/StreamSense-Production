package com.streamsense.recommendationservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "streamsense.recommendations.defaultLimit=3",
        "streamsense.recommendations.maxLimit=6",
        "streamsense.recommendations.signalWindowLimit=5",
        "streamsense.recommendations.experimentName=recommendation-ranking-v1",
        "streamsense.recommendations.activeVariant=balanced",
        "streamsense.recommendations.variants.balanced.enabled=true",
        "streamsense.recommendations.variants.balanced.positiveWeight=1.0",
        "streamsense.recommendations.variants.balanced.sponsorWeight=0.9",
        "streamsense.recommendations.variants.balanced.cautionWeight=1.1",
        "streamsense.recommendations.variants.balanced.momentumThreshold=0.15",
        "streamsense.recommendations.variants.balanced.sponsorConfidenceThreshold=0.65"
})
class RecommendationControllerIntegrationTest {

    private static final MockWebServer SENTIMENT_SERVICE = new MockWebServer();
    private static final MockWebServer VIDEO_SERVICE = new MockWebServer();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsense.services.sentiment-service.base-url", () -> SENTIMENT_SERVICE.url("/").toString());
        registry.add("streamsense.services.video-service.base-url", () -> VIDEO_SERVICE.url("/").toString());
    }

    @BeforeAll
    static void startServers() throws Exception {
        SENTIMENT_SERVICE.start();
        VIDEO_SERVICE.start();
    }

    @AfterAll
    static void shutdownServers() throws Exception {
        SENTIMENT_SERVICE.shutdown();
        VIDEO_SERVICE.shutdown();
    }

    @LocalServerPort
    int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void returnsRecommendationsBackedByRecentSignals() {
        SENTIMENT_SERVICE.enqueue(new MockResponse()
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
                          },
                          {
                            "sentimentEventId": "sent-2",
                            "sourceEventId": "src-2",
                            "streamer": "test",
                            "user": "u2",
                            "message": "love this",
                            "chatTimestamp": 1710000001000,
                            "processedAt": 1710000001500,
                            "label": "POSITIVE",
                            "score": 0.74,
                            "modelVersion": "stub-v1"
                          }
                        ]
                        """));
        VIDEO_SERVICE.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {
                            "detectionEventId": "det-1",
                            "sourceFrameId": "frame-1",
                            "streamer": "test",
                            "frameRef": "frames/1.png",
                            "frameSequence": 1,
                            "capturedAt": 1710000000000,
                            "processedAt": 1710000000500,
                            "sponsor": "Nike",
                            "confidence": 0.91,
                            "modelVersion": "stub-v1",
                            "x": 0.1,
                            "y": 0.2,
                            "width": 0.3,
                            "height": 0.4
                          }
                        ]
                        """));

        ResponseEntity<Recommendation[]> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/recommendations?streamer=test&limit=3",
                Recommendation[].class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isNotEmpty();
        assertThat(response.getBody()[0].streamer()).isEqualTo("test");
        assertThat(response.getBody()[0].experimentName()).isEqualTo("recommendation-ranking-v1");
        assertThat(response.getBody()[0].variantId()).isEqualTo("balanced");
        assertThat(response.getBody()[0].reasons()).isNotEmpty();
    }
}
