package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The session report queries: the summary passes through, the timeline is composed from three services. */
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
            "spring.kafka.consumer.group-id=api-gateway-test-group"
        })
class SessionReportQueryTest {

    private static final MockWebServer SERVICES = new MockWebServer();
    private static final List<String> PATHS = new ArrayList<>();
    private static final long START = 1788816420000L;

    private static final String SESSION =
            """
            {"id": 7, "streamer": "racer", "source": "HELIX", "twitchStreamId": "41", "streamSessionId": null,
             "channelLogin": "racer", "title": "Monza", "category": "F1", "startedAt": %d, "endedAt": %d,
             "live": false, "durationMs": 3600000, "peakViewers": 1500, "averageViewers": 1200.0, "viewerSamples": 30}
            """
                    .formatted(START, START + 3_600_000L);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("streamsense.services.analytics-service.base-url", () -> SERVICES.url("/")
                .toString());
        registry.add("streamsense.services.sentiment-service.base-url", () -> SERVICES.url("/")
                .toString());
        registry.add("streamsense.services.video-service.base-url", () -> SERVICES.url("/")
                .toString());
    }

    @BeforeAll
    static void startServer() throws Exception {
        SERVICES.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath() == null ? "" : request.getPath();
                PATHS.add(path);
                String bare = path.split("\\?")[0];
                return switch (bare) {
                    case "/api/analytics/sessions/7" -> json(SESSION);
                    case "/api/analytics/sessions/7/summary" -> json(
                            """
                            {"session": %s, "sponsor": "Red Bull", "onScreenMs": 2292000, "onScreenShare": 0.637,
                             "mentions": 47, "chatMentions": 31, "voiceMentions": 16, "mentionSentiment": 0.62,
                             "mentionPositiveShare": 0.81, "mentionNegativeShare": 0.06, "averageViewers": 1200.0,
                             "peakViewers": 1500,
                             "risk": {"level": "LOW", "score": 0.14, "factors": []},
                             "chat": {"totalMessages": 2984, "messagesPerMinute": 49.7, "uniqueChatters": 611, "peakMessagesPerMinute": 120},
                             "chatSentiment": {"positive": 1, "neutral": 1, "negative": 0, "averageScore": 0.44, "negativeRatio": 0.09},
                             "transcriptSentiment": {"positive": 1, "neutral": 0, "negative": 0, "averageScore": 0.38, "negativeRatio": 0.07},
                             "engagement": {"spikeCount": 3, "latestSpikeAt": null},
                             "value": {"logoValue": 874.0, "hostReadValue": 314.0, "mediaValue": 1188.0,
                                       "weightedLogoViewerMinutes": 36400.0, "averageProminence": 0.71,
                                       "cpmPer30sEquivalent": 12.0, "hostReadRatePer1000": 15.0, "basis": "test"},
                             "response": {"chatCommand": "!redbull", "commandUses": 137, "commandUsers": 91,
                                          "trackedLinkHost": "redbull.com", "linkPosts": 84}}
                            """
                                    .formatted(SESSION));
                    case "/api/video/detections/range" -> json(
                            """
                            [{"detectionEventId": "d1", "sourceFrameId": "f1", "streamer": "racer", "frameRef": "a",
                              "frameSequence": 1, "capturedAt": %d, "processedAt": %d, "sponsor": "Red Bull",
                              "confidence": 0.9, "modelVersion": "m", "x": 0, "y": 0, "width": 0.2, "height": 0.2,
                              "videoTimestampMs": 5000},
                             {"detectionEventId": "d2", "sourceFrameId": "f2", "streamer": "racer", "frameRef": "b",
                              "frameSequence": 2, "capturedAt": %d, "processedAt": %d, "sponsor": "Red Bull",
                              "confidence": 0.8, "modelVersion": "m", "x": 0, "y": 0, "width": 0.2, "height": 0.2}]
                            """
                                    .formatted(START + 10_000L, START + 10_100L, START + 20_000L, START + 20_100L));
                    case "/api/sentiment/sponsor/range" -> json(
                            """
                            [{"sentimentEventId": "s1", "sourceEventId": "c1", "streamer": "racer", "user": "fan",
                              "message": "red bull car is flying", "chatTimestamp": %d, "processedAt": %d,
                              "label": "POSITIVE", "score": 0.9, "modelVersion": "m", "sponsorRelevant": true,
                              "matchedSponsor": "Red Bull", "matchedTerms": ["red bull"], "relevanceScore": 0.8}]
                            """
                                    .formatted(START + 61_000L, START + 61_500L));
                    case "/api/sentiment/transcript/sponsor/range" -> json("[]");
                    case "/api/analytics/streams/racer/timeseries" -> json(
                            """
                            [{"bucketStart": %d, "bucketEnd": %d, "chatMessageCount": 21, "uniqueChatters": 9,
                              "chatAverageScore": -0.2, "chatNegativeRatio": 0.6, "transcriptAverageScore": null,
                              "transcriptNegativeRatio": null, "sponsorDetectionCount": 0,
                              "estimatedSponsorExposureMs": 0, "engagementSpike": false, "negativeSpike": true}]
                            """
                                    .formatted(START + 900_000L, START + 960_000L));
                    case "/api/analytics/streams/racer/summary" -> json(
                            """
                            {"streamer": "racer", "streamSessionId": null, "windowMinutes": 60, "bucketSizeSeconds": 60,
                             "windowStart": %d, "windowEnd": %d,
                             "chat": {"totalMessages": 1, "messagesPerMinute": 0.1, "uniqueChatters": 1, "peakMessagesPerMinute": 1},
                             "chatSentiment": {"positive": 0, "neutral": 0, "negative": 0, "averageScore": null, "negativeRatio": null},
                             "transcriptSentiment": {"positive": 0, "neutral": 0, "negative": 0, "averageScore": null, "negativeRatio": null},
                             "sponsorExposure": {"totalDetections": 0, "acceptedDetections": 0, "estimatedExposureMs": 0, "topSponsors": []},
                             "engagement": {"spikeCount": 0, "latestSpikeAt": null},
                             "risk": {"level": "LOW_DATA", "score": null, "factors": []},
                             "dataQuality": {"lowData": true, "latestEventAt": null, "aggregationLagMs": null}}
                            """
                                    .formatted(START, START + 3_600_000L));
                    default -> new MockResponse().setResponseCode(404);
                };
            }
        });
        SERVICES.start();
    }

    @AfterAll
    static void shutdownServer() throws Exception {
        SERVICES.shutdown();
    }

    @BeforeEach
    void clearPaths() {
        PATHS.clear();
    }

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Test
    void sessionSummaryPassesTheOptionsThroughAndMapsTheReport() {
        graphQlTester
                .document(
                        """
                        query {
                          sessionSummary(sessionId: "7", sponsor: "Red Bull", chatCommand: "!redbull",
                                         trackedLinkHost: "redbull.com", cpmPer30sEquivalent: 12, hostReadRatePer1000: 15) {
                            sponsor onScreenMs mentions value { mediaValue cpmPer30sEquivalent } response { commandUses linkPosts }
                            session { id title }
                          }
                        }
                        """)
                .execute()
                .path("sessionSummary.sponsor")
                .entity(String.class)
                .isEqualTo("Red Bull")
                .path("sessionSummary.value.mediaValue")
                .entity(Double.class)
                .isEqualTo(1188.0)
                .path("sessionSummary.response.commandUses")
                .entity(Integer.class)
                .isEqualTo(137)
                .path("sessionSummary.session.title")
                .entity(String.class)
                .isEqualTo("Monza");

        assertThat(PATHS)
                .containsExactly("/api/analytics/sessions/7/summary?sponsor=Red%20Bull&chatCommand=!redbull"
                        + "&trackedLinkHost=redbull.com&cpmPer30sEquivalent=12.0&hostReadRatePer1000=15.0");
    }

    @Test
    void sponsorMomentsComposesTheTimelineFromThreeServices() {
        graphQlTester
                .document(
                        """
                        query {
                          sponsorMoments(sessionId: "7", sponsor: "red bull") {
                            sponsor
                            segments { detections offsetMs durationMs videoTimestampMs }
                            chatMoments { count positiveShare sample }
                            riskSpikes { offsetMs }
                            best { kind title }
                            weakest { kind }
                          }
                        }
                        """)
                .execute()
                .path("sponsorMoments.segments[0].detections")
                .entity(Integer.class)
                .isEqualTo(2)
                .path("sponsorMoments.segments[0].offsetMs")
                .entity(Double.class)
                .isEqualTo(10000.0)
                .path("sponsorMoments.chatMoments[0].sample")
                .entity(String.class)
                .isEqualTo("red bull car is flying")
                .path("sponsorMoments.riskSpikes[0].offsetMs")
                .entity(Double.class)
                .isEqualTo(900000.0)
                .path("sponsorMoments.best.kind")
                .entity(String.class)
                .isEqualTo("CHAT_BURST")
                .path("sponsorMoments.weakest")
                .valueIsNull();

        assertThat(PATHS).contains("/api/analytics/sessions/7");
        assertThat(PATHS).anyMatch(path -> path.startsWith("/api/video/detections/range?streamer=racer&from="));
        assertThat(PATHS)
                .anyMatch(path ->
                        path.startsWith("/api/sentiment/sponsor/range?") && path.contains("sponsor=red%20bull"));
        assertThat(PATHS).anyMatch(path -> path.startsWith("/api/analytics/streams/racer/timeseries?from="));
    }

    @Test
    void analyticsQueriesAcceptASessionOrARangeInsteadOfAWindow() {
        graphQlTester
                .document("query { streamMetricsSummary(streamer: \"racer\", sessionId: \"7\") { windowMinutes } }")
                .execute()
                .path("streamMetricsSummary.windowMinutes")
                .entity(Integer.class)
                .isEqualTo(60);
        graphQlTester
                .document("query { streamMetricsSummary(streamer: \"racer\", from: 1, to: 2) { windowMinutes } }")
                .execute()
                .path("streamMetricsSummary.windowMinutes")
                .entity(Integer.class)
                .isEqualTo(60);

        assertThat(PATHS)
                .containsExactly(
                        "/api/analytics/streams/racer/summary?sessionId=7",
                        "/api/analytics/streams/racer/summary?from=1&to=2");
    }

    private static MockResponse json(String body) {
        return new MockResponse().addHeader("Content-Type", "application/json").setBody(body);
    }
}
