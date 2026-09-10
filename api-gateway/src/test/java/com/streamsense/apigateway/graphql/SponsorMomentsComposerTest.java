package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.analytics.SponsorMoments;
import com.streamsense.apigateway.analytics.StreamMetricBucket;
import com.streamsense.apigateway.analytics.StreamSession;
import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.events.SponsorDetectionEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class SponsorMomentsComposerTest {

    private static final long START = 1_788_816_420_000L;

    @Test
    void collapsesDetectionRunsGroupsChatByMinuteAndPicksTheHighlights() {
        StreamSession session = new StreamSession(
                7L,
                "racer",
                "HELIX",
                "41",
                null,
                "racer",
                "Monza",
                "F1",
                START,
                START + 3_600_000L,
                false,
                3_600_000L,
                1500,
                1200.0,
                30,
                null);
        List<SponsorDetectionEvent> detections = List.of(
                detection("Red Bull", START + 10_000L, 0.8, 1_800_000L),
                detection("Red Bull", START + 20_000L, 0.9, null),
                detection("Red Bull", START + 30_000L, 0.7, null),
                // a 5 minute gap starts a new run; a different sponsor never joins one
                detection("Red Bull", START + 330_000L, 0.6, null),
                detection("Nike", START + 335_000L, 0.95, null));
        List<SentimentAnalysisEvent> chat = List.of(
                chat("Red Bull", START + 61_000L, "POSITIVE", 0.8, "livery looks clean"),
                chat("Red Bull", START + 62_000L, "POSITIVE", 0.9, "red bull car is flying"),
                chat("Red Bull", START + 63_000L, "NEUTRAL", 0.1, "rb"),
                chat("Red Bull", START + 900_000L, "NEGATIVE", -0.7, "that drink is gross"),
                chat("Nike", START + 900_000L, "NEGATIVE", -0.9, "nike ad again"));
        List<TranscriptSentimentEvent> voice = List.of(
                voice("Red Bull", START + 120_000L, "POSITIVE", 0.6, "thanks to red bull for tonight"),
                voice("Red Bull", START + 700_000L, "NEGATIVE", -0.71, "tastes like battery acid"));
        List<StreamMetricBucket> buckets =
                List.of(bucket(START + 900_000L, true, 0.6, 21), bucket(START + 960_000L, false, 0.1, 9));

        SponsorMoments moments = SponsorMomentsComposer.compose(
                session, "red bull", detections, chat, voice, buckets, SponsorMomentsComposer.DEFAULT_GAP_MS);

        assertThat(moments.segments()).hasSize(2);
        assertThat(moments.segments().get(0).detections()).isEqualTo(3);
        assertThat(moments.segments().get(0).offsetMs()).isEqualTo(10_000L);
        assertThat(moments.segments().get(0).durationMs()).isEqualTo(20_000L);
        assertThat(moments.segments().get(0).videoTimestampMs()).isEqualTo(1_800_000L);
        assertThat(moments.segments().get(0).peakConfidence()).isEqualTo(0.9);
        assertThat(moments.segments().get(1).detections()).isEqualTo(1);

        assertThat(moments.chatMoments()).hasSize(2);
        assertThat(moments.chatMoments().get(0).count()).isEqualTo(3);
        assertThat(moments.chatMoments().get(0).positiveShare()).isEqualTo(0.667);
        assertThat(moments.chatMoments().get(0).sample()).isEqualTo("red bull car is flying");
        assertThat(moments.voiceMentions()).hasSize(2);
        assertThat(moments.riskSpikes()).hasSize(1);
        assertThat(moments.riskSpikes().get(0).offsetMs()).isEqualTo(900_000L);

        assertThat(moments.best().kind()).isEqualTo("CHAT_BURST");
        assertThat(moments.best().offsetMs()).isEqualTo(60_000L);
        assertThat(moments.best().title()).isEqualTo("3 brand mentions in a minute, 67% positive");
        assertThat(moments.weakest().kind()).isEqualTo("VOICE");
        assertThat(moments.weakest().score()).isEqualTo(-0.71);
    }

    @Test
    void noSponsorMeansAnyRelevantLineAndNoHighlightsWhenNothingQualifies() {
        StreamSession session = new StreamSession(
                8L, "racer", "CAPTURE", null, "racer-1", "racer", null, null, START, null, true, 60_000L, null, null, 0,
                null);
        SponsorMoments moments = SponsorMomentsComposer.compose(
                session,
                null,
                List.of(),
                List.of(chat("Prime", START + 5_000L, "NEUTRAL", 0.0, "prime")),
                List.of(),
                List.of(),
                SponsorMomentsComposer.DEFAULT_GAP_MS);
        assertThat(moments.chatMoments()).hasSize(1);
        assertThat(moments.best()).isNull();
        assertThat(moments.weakest()).isNull();
        assertThat(moments.segments()).isEmpty();
    }

    private static SponsorDetectionEvent detection(String sponsor, long at, double confidence, Long videoTs) {
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId("d-" + at);
        event.setStreamer("racer");
        event.setSponsor(sponsor);
        event.setCapturedAt(at);
        event.setConfidence(confidence);
        event.setVideoTimestampMs(videoTs);
        return event;
    }

    private static SentimentAnalysisEvent chat(String sponsor, long at, String label, double score, String message) {
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId("s-" + at + sponsor);
        event.setStreamer("racer");
        event.setUser("viewer");
        event.setMessage(message);
        event.setChatTimestamp(at);
        event.setLabel(label);
        event.setScore(score);
        event.setSponsorRelevant(true);
        event.setMatchedSponsor(sponsor);
        return event;
    }

    private static TranscriptSentimentEvent voice(String sponsor, long at, String label, double score, String text) {
        TranscriptSentimentEvent event = new TranscriptSentimentEvent();
        event.setSentimentEventId("v-" + at);
        event.setStreamer("racer");
        event.setText(text);
        // A mention is timed at its segment start, where the words begin.
        event.setSegmentStartedAt(at);
        event.setSegmentEndedAt(at + 10_000L);
        event.setLabel(label);
        event.setScore(score);
        event.setSponsorRelevant(true);
        event.setMatchedSponsor(sponsor);
        return event;
    }

    private static StreamMetricBucket bucket(long start, boolean negativeSpike, double negativeRatio, long messages) {
        return new StreamMetricBucket(
                start, start + 60_000L, messages, 5, -0.2, negativeRatio, null, null, 0, 0, false, negativeSpike);
    }
}
