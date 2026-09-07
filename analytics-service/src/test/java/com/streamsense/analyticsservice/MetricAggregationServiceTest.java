package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.events.SentimentAnalysisEvent;
import com.streamsense.analyticsservice.events.SponsorDetectionEvent;
import com.streamsense.analyticsservice.events.TranscriptSentimentEvent;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import com.streamsense.analyticsservice.service.MetricQueryService;
import com.streamsense.analyticsservice.web.RiskFactor;
import com.streamsense.analyticsservice.web.StreamMetricBucket;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.bucket-size-seconds=60",
            "streamsense.analytics.default-window-minutes=15",
            "streamsense.analytics.max-window-minutes=1440",
            "streamsense.analytics.estimated-sponsor-exposure-ms-per-detection=10000",
            "streamsense.analytics.minimum-sponsor-confidence=0.50",
            "streamsense.analytics.negative-spike-ratio-threshold=0.60",
            "streamsense.analytics.negative-spike-minimum-events=2",
            "streamsense.analytics.engagement-spike-minimum-messages=2",
            "streamsense.analytics.engagement-spike-multiplier=2.0",
            "streamsense.analytics.engagement-spike-trailing-window-minutes=5",
            "streamsense.analytics.low-data-minimum-events=1"
        })
class MetricAggregationServiceTest {

    @Autowired
    private MetricAggregationService aggregationService;

    @Autowired
    private MetricQueryService queryService;

    @Autowired
    private StreamSenseProperties properties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aggregatesChatSentimentTranscriptAndSponsorMetrics() throws Exception {
        long now = System.currentTimeMillis();
        String sessionId = "streamer-" + now;

        ChatMessageEvent chat = new ChatMessageEvent();
        chat.setEventId("chat-1");
        chat.setStreamer("test-streamer");
        chat.setUser("viewer-one");
        chat.setMessage("great stream");
        chat.setTimestamp(now);
        chat.setStreamSessionId(sessionId);
        chat.setChannelLogin("test-streamer");

        assertThat(aggregationService.aggregateChatMessage(
                        properties.getTopics().getChatMessages(), chat))
                .isTrue();
        assertThat(aggregationService.aggregateChatMessage(
                        properties.getTopics().getChatMessages(), chat))
                .isFalse();

        SentimentAnalysisEvent sentiment = new SentimentAnalysisEvent();
        sentiment.setSentimentEventId("sentiment-1");
        sentiment.setSourceEventId("chat-1");
        sentiment.setStreamer("test-streamer");
        sentiment.setUser("viewer-one");
        sentiment.setLabel("POSITIVE");
        sentiment.setScore(0.8);
        sentiment.setChatTimestamp(now);
        sentiment.setStreamSessionId(sessionId);
        aggregationService.aggregateChatSentiment(properties.getTopics().getSentimentEvents(), sentiment);

        TranscriptSentimentEvent transcript = new TranscriptSentimentEvent();
        transcript.setSentimentEventId("transcript-sentiment-1");
        transcript.setSegmentId("segment-1");
        transcript.setStreamer("test-streamer");
        transcript.setLabel("NEGATIVE");
        transcript.setScore(-0.4);
        transcript.setSegmentStartedAt(now);
        transcript.setSegmentEndedAt(now + 10000);
        transcript.setStreamSessionId(sessionId);
        aggregationService.aggregateTranscriptSentiment(
                properties.getTopics().getTranscriptSentimentEvents(), transcript);

        SponsorDetectionEvent sponsor = new SponsorDetectionEvent();
        sponsor.setDetectionEventId("sponsor-1");
        sponsor.setSourceFrameId("frame-1");
        sponsor.setStreamer("test-streamer");
        sponsor.setSponsor("Nike");
        sponsor.setConfidence(0.75);
        sponsor.setCapturedAt(now);
        sponsor.setStreamSessionId(sessionId);
        sponsor.setChannelLogin("test-streamer");
        aggregationService.aggregateSponsorDetection(properties.getTopics().getSponsorDetections(), sponsor);

        var summary = queryService.summary("test-streamer", sessionId, 15);
        assertThat(summary.chat().totalMessages()).isEqualTo(1);
        assertThat(summary.chat().uniqueChatters()).isEqualTo(1);
        assertThat(summary.chatSentiment().positive()).isEqualTo(1);
        assertThat(summary.transcriptSentiment().negative()).isEqualTo(1);
        assertThat(summary.sponsorExposure().totalDetections()).isEqualTo(1);
        assertThat(summary.sponsorExposure().estimatedExposureMs()).isEqualTo(10000);
        assertThat(summary.sponsorExposure().topSponsors())
                .extracting("sponsor")
                .containsExactly("Nike");

        mockMvc.perform(get("/api/analytics/streams/test-streamer/summary")
                        .param("streamSessionId", sessionId)
                        .param("windowMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chat.totalMessages").value(1))
                .andExpect(jsonPath("$.sponsorExposure.topSponsors[0].sponsor").value("Nike"));
    }

    @Test
    void emptySummaryReturnsLowDataInsteadOfFakeRisk() throws Exception {
        mockMvc.perform(get("/api/analytics/streams/empty-streamer/summary").param("windowMinutes", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chat.totalMessages").value(0))
                .andExpect(jsonPath("$.risk.level").value("LOW_DATA"))
                .andExpect(jsonPath("$.dataQuality.lowData").value(true));
    }

    @Test
    void sponsorQualityRiskCountsEverySponsorNotJustTheDisplayedTopFive() {
        long now = System.currentTimeMillis();
        String sessionId = "quality-" + now;
        String topic = properties.getTopics().getSponsorDetections();

        for (String sponsor : new String[] {"Alpha", "Bravo", "Charlie", "Delta", "Echo"}) {
            aggregationService.aggregateSponsorDetection(
                    topic, sponsorDetection("quality-" + sponsor, "quality-streamer", sessionId, sponsor, 0.90, now));
        }
        // Foxtrot only has rejected (low-confidence) detections, so it sorts last and falls outside the top five.
        aggregationService.aggregateSponsorDetection(
                topic, sponsorDetection("quality-foxtrot-1", "quality-streamer", sessionId, "Foxtrot", 0.20, now));
        aggregationService.aggregateSponsorDetection(
                topic, sponsorDetection("quality-foxtrot-2", "quality-streamer", sessionId, "Foxtrot", 0.25, now));

        var summary = queryService.summary("quality-streamer", sessionId, 15);
        assertThat(summary.sponsorExposure().totalDetections()).isEqualTo(7);
        assertThat(summary.sponsorExposure().acceptedDetections()).isEqualTo(5);
        assertThat(summary.sponsorExposure().topSponsors())
                .hasSize(5)
                .extracting("sponsor")
                .doesNotContain("Foxtrot");

        RiskFactor sponsorQuality = summary.risk().factors().stream()
                .filter(factor -> factor.name().equals("sponsorQualityRisk"))
                .findFirst()
                .orElseThrow();
        assertThat(sponsorQuality.value()).isCloseTo(2.0d / 7.0d, within(1e-9));
    }

    @Test
    void timeseriesCarriesSponsorTotalsForEachBucket() {
        long now = System.currentTimeMillis();
        String sessionId = "timeseries-" + now;
        String topic = properties.getTopics().getSponsorDetections();
        long threeMinutesAgo = now - 3 * 60_000L;

        aggregationService.aggregateSponsorDetection(
                topic, sponsorDetection("ts-1", "timeseries-streamer", sessionId, "Nike", 0.80, now));
        aggregationService.aggregateSponsorDetection(
                topic, sponsorDetection("ts-2", "timeseries-streamer", sessionId, "Adidas", 0.85, now));
        aggregationService.aggregateSponsorDetection(
                topic, sponsorDetection("ts-3", "timeseries-streamer", sessionId, "Nike", 0.30, threeMinutesAgo));

        List<StreamMetricBucket> buckets = queryService.timeseries("timeseries-streamer", sessionId, 15, null);
        Map<Long, StreamMetricBucket> byStart =
                buckets.stream().collect(Collectors.toMap(StreamMetricBucket::bucketStart, Function.identity()));
        long currentBucket = Math.floorDiv(now, 60_000L) * 60_000L;
        long earlierBucket = Math.floorDiv(threeMinutesAgo, 60_000L) * 60_000L;

        assertThat(buckets).hasSize(15);
        assertThat(byStart.get(currentBucket).sponsorDetectionCount()).isEqualTo(2);
        assertThat(byStart.get(currentBucket).estimatedSponsorExposureMs()).isEqualTo(20000);
        assertThat(byStart.get(earlierBucket).sponsorDetectionCount()).isEqualTo(1);
        assertThat(byStart.get(earlierBucket).estimatedSponsorExposureMs()).isZero();
        assertThat(buckets.stream()
                        .filter(bucket ->
                                bucket.bucketStart() != currentBucket && bucket.bucketStart() != earlierBucket)
                        .mapToLong(StreamMetricBucket::sponsorDetectionCount)
                        .sum())
                .isZero();
    }

    private static SponsorDetectionEvent sponsorDetection(
            String detectionEventId,
            String streamer,
            String sessionId,
            String sponsor,
            double confidence,
            long capturedAt) {
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId(detectionEventId);
        event.setSourceFrameId("frame-" + detectionEventId);
        event.setStreamer(streamer);
        event.setSponsor(sponsor);
        event.setConfidence(confidence);
        event.setCapturedAt(capturedAt);
        event.setStreamSessionId(sessionId);
        event.setChannelLogin(streamer);
        return event;
    }
}
