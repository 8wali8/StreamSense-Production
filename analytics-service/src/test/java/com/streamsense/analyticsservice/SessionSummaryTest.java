package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.api.SessionSummary;
import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.SummaryOptions;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.events.SentimentAnalysisEvent;
import com.streamsense.analyticsservice.events.SponsorDetectionEvent;
import com.streamsense.analyticsservice.events.TranscriptSentimentEvent;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import com.streamsense.analyticsservice.service.SessionSummaryService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.HelixStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The report numbers for one session, end to end from events to the REST summary. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-summary-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.estimated-sponsor-exposure-ms-per-detection=10000",
            "streamsense.analytics.minimum-sponsor-confidence=0.50",
            "streamsense.analytics.low-data-minimum-events=1",
            "streamsense.analytics.capture-session-close-check-ms=3600000",
            "streamsense.analytics.value.cpm-per-30s-equivalent=10.0",
            "streamsense.analytics.value.host-read-rate-per-1000=20.0",
            "streamsense.analytics.response.default-chat-command=!redbull"
        })
class SessionSummaryTest {

    private static final String STREAMER = "racer";

    @Autowired
    private MetricAggregationService aggregation;

    @Autowired
    private StreamSessionService sessions;

    @Autowired
    private SessionSummaryService summaries;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void summarisesExposureMentionsValueAndResponseForASession() throws Exception {
        long now = System.currentTimeMillis();
        long start = Math.floorDiv(now - 30 * 60_000L, 60_000L) * 60_000L;

        // A Helix session with two viewer samples (1000 then 2000 viewers) that started 30 minutes ago.
        sessions.recordHelixLive(new HelixStream("41", "Racer", "Monza", "F1", 1000, start));
        sessions.recordHelixLive(new HelixStream("41", "Racer", "Monza", "F1", 2000, start));
        StreamSession session = sessions.list(STREAMER, null, null, null).get(0);

        // Three accepted Red Bull detections (box 20% x 25% = 5% of the frame) in one minute, one rejected.
        for (int i = 0; i < 3; i++) {
            aggregation.aggregateSponsorDetection(
                    "t", detection("d" + i, start + 60_000L + i * 10_000L, 0.9, 0.2, 0.25));
        }
        aggregation.aggregateSponsorDetection("t", detection("low", start + 60_000L, 0.3, 0.2, 0.25));
        // Two relevant chat lines and one voice line, one unrelated chat line.
        aggregation.aggregateChatSentiment("s", chat("c1", start + 120_000L, "POSITIVE", 0.8, true, "Red Bull"));
        aggregation.aggregateChatSentiment("s", chat("c2", start + 121_000L, "NEGATIVE", -0.4, true, "red bull"));
        aggregation.aggregateChatSentiment("s", chat("c3", start + 122_000L, "POSITIVE", 0.9, false, null));
        aggregation.aggregateTranscriptSentiment("v", voice("v1", start + 180_000L, "POSITIVE", 0.6, "Red Bull"));
        // Commands and links in chat.
        aggregation.aggregateChatMessage("m", message("m1", start + 200_000L, "alice", "!redbull"));
        aggregation.aggregateChatMessage("m", message("m2", start + 201_000L, "alice", "!REDBULL again"));
        aggregation.aggregateChatMessage("m", message("m3", start + 202_000L, "bob", "!redbull"));
        aggregation.aggregateChatMessage(
                "m", message("m4", start + 203_000L, "bob", "go to https://www.redbull.com/f1 now"));
        aggregation.aggregateChatMessage("m", message("m5", start + 204_000L, "cara", "hello"));

        SessionSummary summary = summaries
                .summary(session.id(), new SummaryOptions(null, null, "redbull.com", null, null))
                .orElseThrow();

        assertThat(summary.sponsor()).isEqualTo("Red Bull");
        assertThat(summary.onScreenMs()).isEqualTo(30_000L);
        assertThat(summary.chatMentions()).isEqualTo(2);
        assertThat(summary.voiceMentions()).isEqualTo(1);
        assertThat(summary.mentionSentiment()).isCloseTo(0.333, within(0.001));
        assertThat(summary.mentionPositiveShare()).isCloseTo(0.667, within(0.001));
        assertThat(summary.mentionNegativeShare()).isCloseTo(0.333, within(0.001));
        assertThat(summary.peakViewers()).isEqualTo(2000);
        assertThat(summary.averageViewers()).isEqualTo(1500.0);
        assertThat(summary.risk().level()).isNotNull();

        // Value: 0.5 minutes on screen, viewers at that bucket = last sample 2000 (both samples are at "now",
        // after the bucket, so the first sample 1000 applies), weight 0.5 + min(0.5, 0.05 * 10) = 1.0:
        // weighted viewer-minutes = 0.5 * 1000 * 1.0 = 500; logo value = 500 * 2 / 1000 * 10 = 10.0;
        // host reads = 1 * 1500 / 1000 * 20 = 30.0.
        assertThat(summary.value().weightedLogoViewerMinutes()).isCloseTo(500.0, within(0.001));
        assertThat(summary.value().averageProminence()).isEqualTo(1.0);
        assertThat(summary.value().logoValue()).isEqualTo(10.0);
        assertThat(summary.value().hostReadValue()).isEqualTo(30.0);
        assertThat(summary.value().mediaValue()).isEqualTo(40.0);
        assertThat(summary.value().cpmPer30sEquivalent()).isEqualTo(10.0);

        assertThat(summary.response().chatCommand()).isEqualTo("!redbull");
        assertThat(summary.response().commandUses()).isEqualTo(3);
        assertThat(summary.response().commandUsers()).isEqualTo(2);
        assertThat(summary.response().trackedLinkHost()).isEqualTo("redbull.com");
        assertThat(summary.response().linkPosts()).isEqualTo(1);

        mockMvc.perform(get("/api/analytics/sessions/" + session.id() + "/summary")
                        .param("sponsor", "nike"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsor").value("nike"))
                .andExpect(jsonPath("$.onScreenMs").value(0))
                .andExpect(jsonPath("$.value.mediaValue").value(0.0));
        mockMvc.perform(get("/api/analytics/sessions/999999/summary")).andExpect(status().isNotFound());

        // The existing analytics endpoints accept the session and an absolute range.
        mockMvc.perform(get("/api/analytics/streams/" + STREAMER + "/summary")
                        .param("sessionId", String.valueOf(session.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sponsorExposure.estimatedExposureMs").value(30000));
        mockMvc.perform(get("/api/analytics/streams/" + STREAMER + "/timeseries")
                        .param("from", String.valueOf(start))
                        .param("to", String.valueOf(start + 5 * 60_000L))
                        .param("bucketSeconds", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(get("/api/analytics/streams/" + STREAMER + "/summary").param("from", String.valueOf(start)))
                .andExpect(status().isBadRequest());
    }

    private static SponsorDetectionEvent detection(String id, long at, double confidence, double width, double height) {
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId(id);
        event.setSourceFrameId("frame-" + id);
        event.setStreamer(STREAMER);
        event.setFrameRef("frames/" + id + ".png");
        event.setCapturedAt(at);
        event.setProcessedAt(at + 100);
        event.setSponsor("Red Bull");
        event.setConfidence(confidence);
        event.setModelVersion("test");
        event.setX(0.1);
        event.setY(0.1);
        event.setWidth(width);
        event.setHeight(height);
        return event;
    }

    private static SentimentAnalysisEvent chat(
            String id, long at, String label, double score, boolean relevant, String sponsor) {
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId(id);
        event.setSourceEventId("src-" + id);
        event.setStreamer(STREAMER);
        event.setUser("viewer");
        event.setMessage("msg");
        event.setChatTimestamp(at);
        event.setProcessedAt(at + 100);
        event.setLabel(label);
        event.setScore(score);
        event.setModelVersion("test");
        event.setSponsorRelevant(relevant);
        event.setMatchedSponsor(sponsor);
        return event;
    }

    private static TranscriptSentimentEvent voice(String id, long at, String label, double score, String sponsor) {
        TranscriptSentimentEvent event = new TranscriptSentimentEvent();
        event.setSentimentEventId(id);
        event.setSegmentId("seg-" + id);
        event.setStreamer(STREAMER);
        event.setText("text");
        event.setSegmentStartedAt(at - 3000);
        event.setSegmentEndedAt(at);
        event.setProcessedAt(at + 100);
        event.setLabel(label);
        event.setScore(score);
        event.setModelVersion("test");
        event.setTranscriptModelVersion("test");
        event.setTranscriptSequence(1);
        event.setSponsorRelevant(true);
        event.setMatchedSponsor(sponsor);
        return event;
    }

    private static ChatMessageEvent message(String id, long at, String user, String text) {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId(id);
        event.setStreamer(STREAMER);
        event.setUser(user);
        event.setMessage(text);
        event.setTimestamp(at);
        return event;
    }
}
