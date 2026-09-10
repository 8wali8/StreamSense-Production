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

/** Under a share token only the report queries run, only for the shared deal, and never with the fee. */
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
class ShareLinkQueryTest {

    private static final MockWebServer MOCK_WEB_SERVER = new MockWebServer();

    private static final String DEAL_JSON =
            """
            {"id": 3, "streamer": "redbull-testing", "sponsor": "Red Bull", "startsAt": 1788400000000,
             "endsAt": null, "promisedStreams": 4, "fee": 2500.0, "currency": "USD", "cpmPer30sEquivalent": 12.0,
             "hostReadRatePer1000": 15.0, "trackedLink": "https://www.redbull.com/f1", "trackedLinkHost": "redbull.com",
             "chatCommand": "!redbull", "channelPointReward": null, "active": true, "shareToken": "tok-abc",
             "createdAt": 1788400000000}
            """;

    private static final String SESSION_JSON =
            """
            {"id": 7, "streamer": "redbull-testing", "source": "CAPTURE", "twitchStreamId": "2750461300",
             "streamSessionId": "s", "channelLogin": "redbull-testing", "title": "Monza", "category": null,
             "startedAt": 1788816420000, "endedAt": 1788824460000, "live": false, "durationMs": 8040000,
             "peakViewers": null, "averageViewers": null, "viewerSamples": 0}
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

    private HttpGraphQlTester shared(String token) {
        return graphQlTester.mutate().header(ShareLinkInterceptor.HEADER, token).build();
    }

    private static MockResponse json(String body) {
        return new MockResponse().addHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void theSharedDealIsReadableWithoutItsFeeAndOtherDealsAreNot() throws Exception {
        MOCK_WEB_SERVER.enqueue(json(DEAL_JSON));
        MOCK_WEB_SERVER.enqueue(
                json(
                        "{\"deal\": " + DEAL_JSON
                                + """
                , "totals": {"streams": 0, "liveStreams": 0, "streamedMs": 0, "onScreenMs": 0, "onScreenShare": null,
                   "mentions": 0, "chatMentions": 0, "voiceMentions": 0, "mentionSentiment": null, "averageViewers": null,
                   "logoValue": null, "hostReadValue": null, "mediaValue": null, "commandUses": 0, "linkPosts": 0},
                  "sessions": []}
                """));

        shared("tok-abc")
                .document("query { dealSummary(id: \"3\") { deal { id sponsor fee shareToken } totals { streams } } }")
                .execute()
                .path("dealSummary.deal.sponsor")
                .entity(String.class)
                .isEqualTo("Red Bull")
                .path("dealSummary.deal.fee")
                .valueIsNull()
                .path("dealSummary.deal.shareToken")
                .valueIsNull();
        assertThat(MOCK_WEB_SERVER.takeRequest().getPath()).isEqualTo("/api/analytics/share/tok-abc");
        assertThat(MOCK_WEB_SERVER.takeRequest().getPath()).isEqualTo("/api/analytics/deals/3/summary");

        // Another deal resolves to null without touching analytics beyond the token check.
        MOCK_WEB_SERVER.enqueue(json(DEAL_JSON));
        int before = MOCK_WEB_SERVER.getRequestCount();
        shared("tok-abc")
                .document("query { deal(id: \"4\") { id } }")
                .execute()
                .path("deal")
                .valueIsNull();
        assertThat(MOCK_WEB_SERVER.getRequestCount() - before).isEqualTo(1);
    }

    @Test
    void aSessionOutsideTheSharedDealIsHiddenAndOneInsideIsPricedAtTheDealsSponsor() throws Exception {
        String summary =
                "{\"session\": " + SESSION_JSON + ", \"dealId\": %s, \"sponsor\": \"Red Bull\", \"onScreenMs\": 0,"
                        + " \"mentions\": 0, \"chatMentions\": 0, \"voiceMentions\": 0,"
                        + " \"risk\": {\"level\": \"LOW_DATA\", \"factors\": []},"
                        + " \"chat\": {\"totalMessages\": 0, \"messagesPerMinute\": 0, \"uniqueChatters\": 0, \"peakMessagesPerMinute\": 0},"
                        + " \"chatSentiment\": {\"positive\": 0, \"neutral\": 0, \"negative\": 0},"
                        + " \"transcriptSentiment\": {\"positive\": 0, \"neutral\": 0, \"negative\": 0},"
                        + " \"engagement\": {\"spikeCount\": 0},"
                        + " \"value\": {\"cpmPer30sEquivalent\": 12, \"hostReadRatePer1000\": 15, \"basis\": \"b\"},"
                        + " \"response\": {\"commandUses\": 0, \"commandUsers\": 0, \"linkPosts\": 0}}";

        MOCK_WEB_SERVER.enqueue(json(DEAL_JSON));
        MOCK_WEB_SERVER.enqueue(json(summary.formatted("3")));
        shared("tok-abc")
                .document("query { sessionSummary(sessionId: \"7\", sponsor: \"Nike\") { sponsor dealId } }")
                .execute()
                .path("sessionSummary.sponsor")
                .entity(String.class)
                .isEqualTo("Red Bull");
        MOCK_WEB_SERVER.takeRequest();
        // The caller's sponsor is ignored: the summary is asked for the deal's sponsor.
        assertThat(MOCK_WEB_SERVER.takeRequest().getPath())
                .isEqualTo("/api/analytics/sessions/7/summary?sponsor=Red%20Bull");

        MOCK_WEB_SERVER.enqueue(json(DEAL_JSON));
        MOCK_WEB_SERVER.enqueue(json(summary.formatted("9")));
        shared("tok-abc")
                .document("query { sessionSummary(sessionId: \"7\") { sponsor } }")
                .execute()
                .path("sessionSummary")
                .valueIsNull();
        MOCK_WEB_SERVER.takeRequest();
        MOCK_WEB_SERVER.takeRequest();
    }

    @Test
    void otherQueriesAndBadTokensAreRefusedWithStableCodes() {
        shared("tok-abc")
                .document("query { deals(streamer: \"redbull-testing\") { id } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> assertThat(
                                error.getExtensions())
                        .containsEntry("code", "SHARE_FORBIDDEN")));

        MOCK_WEB_SERVER.enqueue(new MockResponse().setResponseCode(404));
        shared("nope")
                .document("query { deal(id: \"3\") { id } }")
                .execute()
                .errors()
                .satisfy(errors -> assertThat(errors).singleElement().satisfies(error -> assertThat(
                                error.getExtensions())
                        .containsEntry("code", "SHARE_TOKEN_INVALID")));
    }
}
