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
class DealsQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    private static final String DEAL_JSON =
            """
            {"id": 3, "streamer": "redbull-testing", "sponsor": "Red Bull", "startsAt": 1788400000000,
             "endsAt": null, "promisedStreams": 4, "fee": 2500.0, "currency": "USD", "cpmPer30sEquivalent": 12.0,
             "hostReadRatePer1000": 15.0, "trackedLink": "https://www.redbull.com/f1", "trackedLinkHost": "redbull.com",
             "chatCommand": "!redbull", "channelPointReward": null, "active": true, "shareToken": "tok-abc", "createdAt": 1788400000000}
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
    void dealsListAndSummaryMapThroughFromAnalytics() throws Exception {
        MOCK_WEB_SERVER.enqueue(
                new MockResponse().addHeader("Content-Type", "application/json").setBody("[" + DEAL_JSON + "]"));
        MOCK_WEB_SERVER.enqueue(
                new MockResponse()
                        .addHeader("Content-Type", "application/json")
                        .setBody(
                                "{\"deal\": " + DEAL_JSON
                                        + """
                        , "totals": {"streams": 2, "liveStreams": 0, "streamedMs": 7200000, "onScreenMs": 900000,
                           "onScreenShare": 0.125, "mentions": 40, "chatMentions": 30, "voiceMentions": 10,
                           "mentionSentiment": 0.5, "averageViewers": 1200.0, "logoValue": 500.0, "hostReadValue": 100.0,
                           "mediaValue": 600.0, "commandUses": 12, "linkPosts": 3}, "sessions": []}
                        """));

        graphQlTester
                .document(
                        """
                        query {
                          deals(streamer: "redbull-testing", limit: 10) { id sponsor chatCommand trackedLinkHost active fee }
                        }
                        """)
                .execute()
                .path("deals[0].id")
                .entity(String.class)
                .isEqualTo("3")
                .path("deals[0].trackedLinkHost")
                .entity(String.class)
                .isEqualTo("redbull.com")
                .path("deals[0].fee")
                .entity(Double.class)
                .isEqualTo(2500.0);
        RecordedRequest listRequest = MOCK_WEB_SERVER.takeRequest();
        assertThat(listRequest.getPath()).isEqualTo("/api/analytics/deals?streamer=redbull-testing&limit=10");

        graphQlTester
                .document(
                        """
                        query {
                          dealSummary(id: "3") { deal { id } totals { streams mediaValue onScreenShare } sessions { sponsor } }
                        }
                        """)
                .execute()
                .path("dealSummary.totals.streams")
                .entity(Integer.class)
                .isEqualTo(2)
                .path("dealSummary.totals.mediaValue")
                .entity(Double.class)
                .isEqualTo(600.0);
        assertThat(MOCK_WEB_SERVER.takeRequest().getPath()).isEqualTo("/api/analytics/deals/3/summary");
    }

    @Test
    void unknownOrMalformedDealIdsResolveToNull() {
        int requestsBefore = MOCK_WEB_SERVER.getRequestCount();
        MOCK_WEB_SERVER.enqueue(new MockResponse().setResponseCode(404));

        graphQlTester
                .document("query { deal(id: \"999\") { id } }")
                .execute()
                .path("deal")
                .valueIsNull();
        graphQlTester
                .document("query { deal(id: \"x\") { id } }")
                .execute()
                .path("deal")
                .valueIsNull();
        assertThat(MOCK_WEB_SERVER.getRequestCount() - requestsBefore).isEqualTo(1);
    }
}
