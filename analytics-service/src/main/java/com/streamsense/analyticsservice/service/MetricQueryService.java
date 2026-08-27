package com.streamsense.analyticsservice.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.model.SponsorBucketMetric;
import com.streamsense.analyticsservice.model.StreamBucketMetric;
import com.streamsense.analyticsservice.persistence.MetricBucketRepository;
import com.streamsense.analyticsservice.persistence.SponsorMetricBucketRepository;
import com.streamsense.analyticsservice.web.AnalyticsDataQuality;
import com.streamsense.analyticsservice.web.BrandSafetyMetrics;
import com.streamsense.analyticsservice.web.ChatMetrics;
import com.streamsense.analyticsservice.web.EngagementMetrics;
import com.streamsense.analyticsservice.web.RiskFactor;
import com.streamsense.analyticsservice.web.SentimentMetricSummary;
import com.streamsense.analyticsservice.web.SponsorExposureMetric;
import com.streamsense.analyticsservice.web.SponsorExposureSummary;
import com.streamsense.analyticsservice.web.StreamMetricBucket;
import com.streamsense.analyticsservice.web.StreamMetricsSummary;

@Service
public class MetricQueryService {

    private final StreamSenseProperties properties;
    private final MetricBucketRepository metricBuckets;
    private final SponsorMetricBucketRepository sponsorBuckets;
    private final Clock clock;

    @Autowired
    public MetricQueryService(StreamSenseProperties properties, MetricBucketRepository metricBuckets,
            SponsorMetricBucketRepository sponsorBuckets) {
        this(properties, metricBuckets, sponsorBuckets, Clock.systemUTC());
    }

    MetricQueryService(StreamSenseProperties properties, MetricBucketRepository metricBuckets,
            SponsorMetricBucketRepository sponsorBuckets, Clock clock) {
        this.properties = properties;
        this.metricBuckets = metricBuckets;
        this.sponsorBuckets = sponsorBuckets;
        this.clock = clock;
    }

    public StreamMetricsSummary summary(String streamer, String streamSessionId, Integer requestedWindowMinutes) {
        QueryWindow window = window(streamer, streamSessionId, requestedWindowMinutes, null);
        List<StreamBucketMetric> buckets = metricBuckets.findBuckets(window.streamer(), window.sessionKey(),
                window.windowStart(), window.windowEnd(), window.bucketSizeSeconds());
        List<SponsorExposureMetric> sponsorMetrics = sponsorExposure(window.streamer(), window.streamSessionId(),
                window.windowMinutes());

        long totalMessages = buckets.stream().mapToLong(StreamBucketMetric::chatMessageCount).sum();
        long uniqueChatters = metricBuckets.countUniqueChatters(window.streamer(), window.sessionKey(),
                window.windowStart(), window.windowEnd(), window.bucketSizeSeconds());
        long peakMessages = buckets.stream().mapToLong(StreamBucketMetric::chatMessageCount).max().orElse(0);
        double messagesPerMinute = window.windowMinutes() == 0 ? 0.0d : (double) totalMessages / window.windowMinutes();

        SentimentMetricSummary chatSentiment = chatSentimentSummary(buckets);
        SentimentMetricSummary transcriptSentiment = transcriptSentimentSummary(buckets);
        SponsorExposureSummary sponsorSummary = sponsorSummary(sponsorMetrics);
        // Summed over every sponsor in the window; the summary only keeps the top five for display.
        long lowConfidenceDetections = sponsorMetrics.stream()
                .mapToLong(SponsorExposureMetric::lowConfidenceDetectionCount)
                .sum();
        EngagementMetrics engagement = engagementSummary(buckets);
        BrandSafetyMetrics risk = risk(chatSentiment, transcriptSentiment, sponsorSummary, lowConfidenceDetections,
                engagement,
                totalMessages + chatSentiment.positive() + chatSentiment.neutral() + chatSentiment.negative()
                        + transcriptSentiment.positive() + transcriptSentiment.neutral() + transcriptSentiment.negative()
                        + sponsorSummary.totalDetections());
        Long latestEventAt = metricBuckets.latestEventAt(window.streamer(), window.sessionKey());

        return new StreamMetricsSummary(
                window.streamer(),
                window.streamSessionId(),
                window.windowMinutes(),
                window.bucketSizeSeconds(),
                window.windowStart(),
                window.windowEnd(),
                new ChatMetrics(totalMessages, messagesPerMinute, uniqueChatters, peakMessages),
                chatSentiment,
                transcriptSentiment,
                sponsorSummary,
                engagement,
                risk,
                new AnalyticsDataQuality(isLowData(totalMessages, chatSentiment, transcriptSentiment, sponsorSummary),
                        latestEventAt, latestEventAt == null ? null : Math.max(0, clock.millis() - latestEventAt)));
    }

    public List<StreamMetricBucket> timeseries(String streamer, String streamSessionId, Integer requestedWindowMinutes,
            Integer requestedBucketSeconds) {
        QueryWindow window = window(streamer, streamSessionId, requestedWindowMinutes, requestedBucketSeconds);
        List<StreamBucketMetric> storedBuckets = metricBuckets.findBuckets(window.streamer(), window.sessionKey(),
                window.windowStart(), window.windowEnd(), window.bucketSizeSeconds());
        Map<Long, StreamBucketMetric> byStart = storedBuckets.stream()
                .collect(Collectors.toMap(StreamBucketMetric::bucketStart, Function.identity(), this::mergeBuckets));
        List<StreamMetricBucket> response = new ArrayList<>();
        long bucketMs = window.bucketSizeSeconds() * 1000L;
        for (long bucketStart = window.windowStart(); bucketStart < window.windowEnd(); bucketStart += bucketMs) {
            StreamBucketMetric bucket = byStart.get(bucketStart);
            long sponsorDetectionCount = sponsorBuckets.countSponsorDetections(window.streamer(), window.sessionKey(),
                    bucketStart, window.bucketSizeSeconds());
            long sponsorExposureMs = sponsorBuckets.sumSponsorExposure(window.streamer(), window.sessionKey(),
                    bucketStart, window.bucketSizeSeconds());
            response.add(toResponseBucket(bucketStart, bucketMs, bucket, sponsorDetectionCount, sponsorExposureMs));
        }
        return response;
    }

    public List<SponsorExposureMetric> sponsorExposure(String streamer, String streamSessionId, Integer requestedWindowMinutes) {
        QueryWindow window = window(streamer, streamSessionId, requestedWindowMinutes, null);
        return sponsorBuckets.findSponsorMetrics(window.streamer(), window.sessionKey(), window.windowStart(),
                window.windowEnd(), window.bucketSizeSeconds()).stream()
                .map(this::toSponsorExposure)
                .sorted(Comparator.comparingLong(SponsorExposureMetric::acceptedDetectionCount).reversed()
                        .thenComparing(SponsorExposureMetric::sponsor))
                .toList();
    }

    public BrandSafetyMetrics risk(String streamer, String streamSessionId, Integer requestedWindowMinutes) {
        return summary(streamer, streamSessionId, requestedWindowMinutes).risk();
    }

    private StreamMetricBucket toResponseBucket(long bucketStart, long bucketMs, StreamBucketMetric bucket,
            long sponsorDetectionCount, long sponsorExposureMs) {
        if (bucket == null) {
            return new StreamMetricBucket(bucketStart, bucketStart + bucketMs, 0, 0, null, null, null, null,
                    sponsorDetectionCount, sponsorExposureMs, false, false);
        }
        return new StreamMetricBucket(
                bucketStart,
                bucketStart + bucketMs,
                bucket.chatMessageCount(),
                bucket.uniqueChatters(),
                average(bucket.chatScoreSum(), bucket.chatSentimentCount()),
                ratio(bucket.chatNegativeCount(), bucket.chatSentimentCount()),
                average(bucket.transcriptScoreSum(), bucket.transcriptSentimentCount()),
                ratio(bucket.transcriptNegativeCount(), bucket.transcriptSentimentCount()),
                sponsorDetectionCount,
                sponsorExposureMs,
                bucket.engagementSpikeCount() > 0,
                bucket.negativeSpikeCount() > 0);
    }

    private StreamBucketMetric mergeBuckets(StreamBucketMetric left, StreamBucketMetric right) {
        return new StreamBucketMetric(
                left.bucketStart(),
                left.bucketSizeSeconds(),
                left.chatMessageCount() + right.chatMessageCount(),
                left.uniqueChatters() + right.uniqueChatters(),
                left.chatSentimentCount() + right.chatSentimentCount(),
                left.chatPositiveCount() + right.chatPositiveCount(),
                left.chatNeutralCount() + right.chatNeutralCount(),
                left.chatNegativeCount() + right.chatNegativeCount(),
                left.chatScoreSum() + right.chatScoreSum(),
                left.transcriptSentimentCount() + right.transcriptSentimentCount(),
                left.transcriptPositiveCount() + right.transcriptPositiveCount(),
                left.transcriptNeutralCount() + right.transcriptNeutralCount(),
                left.transcriptNegativeCount() + right.transcriptNegativeCount(),
                left.transcriptScoreSum() + right.transcriptScoreSum(),
                left.negativeSpikeCount() + right.negativeSpikeCount(),
                left.engagementSpikeCount() + right.engagementSpikeCount());
    }

    private SentimentMetricSummary chatSentimentSummary(List<StreamBucketMetric> buckets) {
        long positive = buckets.stream().mapToLong(StreamBucketMetric::chatPositiveCount).sum();
        long neutral = buckets.stream().mapToLong(StreamBucketMetric::chatNeutralCount).sum();
        long negative = buckets.stream().mapToLong(StreamBucketMetric::chatNegativeCount).sum();
        long count = buckets.stream().mapToLong(StreamBucketMetric::chatSentimentCount).sum();
        double scoreSum = buckets.stream().mapToDouble(StreamBucketMetric::chatScoreSum).sum();
        return new SentimentMetricSummary(positive, neutral, negative, average(scoreSum, count), ratio(negative, count));
    }

    private SentimentMetricSummary transcriptSentimentSummary(List<StreamBucketMetric> buckets) {
        long positive = buckets.stream().mapToLong(StreamBucketMetric::transcriptPositiveCount).sum();
        long neutral = buckets.stream().mapToLong(StreamBucketMetric::transcriptNeutralCount).sum();
        long negative = buckets.stream().mapToLong(StreamBucketMetric::transcriptNegativeCount).sum();
        long count = buckets.stream().mapToLong(StreamBucketMetric::transcriptSentimentCount).sum();
        double scoreSum = buckets.stream().mapToDouble(StreamBucketMetric::transcriptScoreSum).sum();
        return new SentimentMetricSummary(positive, neutral, negative, average(scoreSum, count), ratio(negative, count));
    }

    private SponsorExposureSummary sponsorSummary(List<SponsorExposureMetric> sponsors) {
        long total = sponsors.stream().mapToLong(SponsorExposureMetric::detectionCount).sum();
        long accepted = sponsors.stream().mapToLong(SponsorExposureMetric::acceptedDetectionCount).sum();
        long exposure = sponsors.stream().mapToLong(SponsorExposureMetric::estimatedExposureMs).sum();
        return new SponsorExposureSummary(total, accepted, exposure, sponsors.stream().limit(5).toList());
    }

    private EngagementMetrics engagementSummary(List<StreamBucketMetric> buckets) {
        long spikes = buckets.stream().mapToLong(StreamBucketMetric::engagementSpikeCount).sum();
        Long latest = buckets.stream()
                .filter(bucket -> bucket.engagementSpikeCount() > 0)
                .map(StreamBucketMetric::bucketStart)
                .max(Long::compareTo)
                .orElse(null);
        return new EngagementMetrics(spikes, latest);
    }

    private BrandSafetyMetrics risk(SentimentMetricSummary chat, SentimentMetricSummary transcript,
            SponsorExposureSummary sponsors, long lowConfidenceDetections, EngagementMetrics engagement,
            long totalSignals) {
        if (totalSignals < properties.getAnalytics().getLowDataMinimumEvents()) {
            return new BrandSafetyMetrics("LOW_DATA", null, List.of());
        }
        double chatNegativeRatio = value(chat.negativeRatio());
        double transcriptNegativeRatio = value(transcript.negativeRatio());
        double negativeSpikeScore = Math.min(1.0d, engagement.spikeCount() / 5.0d);
        double sponsorQualityRisk = sponsors.totalDetections() == 0 ? 0.0d
                : lowConfidenceDetections / (double) sponsors.totalDetections();
        double negativeEngagementSpikeRisk = chatNegativeRatio >= 0.50d ? negativeSpikeScore : 0.0d;
        List<RiskFactor> factors = List.of(
                new RiskFactor("chatNegativeRatio", chatNegativeRatio, 0.35d),
                new RiskFactor("transcriptNegativeRatio", transcriptNegativeRatio, 0.25d),
                new RiskFactor("negativeSpikeScore", negativeSpikeScore, 0.20d),
                new RiskFactor("sponsorQualityRisk", sponsorQualityRisk, 0.10d),
                new RiskFactor("negativeEngagementSpikeRisk", negativeEngagementSpikeRisk, 0.10d));
        double score = clamp(
                chatNegativeRatio * 0.35d
                        + transcriptNegativeRatio * 0.25d
                        + negativeSpikeScore * 0.20d
                        + sponsorQualityRisk * 0.10d
                        + negativeEngagementSpikeRisk * 0.10d);
        return new BrandSafetyMetrics(riskLevel(score), round(score), factors);
    }

    private boolean isLowData(long totalMessages, SentimentMetricSummary chat, SentimentMetricSummary transcript,
            SponsorExposureSummary sponsors) {
        long total = totalMessages + chat.positive() + chat.neutral() + chat.negative()
                + transcript.positive() + transcript.neutral() + transcript.negative() + sponsors.totalDetections();
        return total < properties.getAnalytics().getLowDataMinimumEvents();
    }

    private SponsorExposureMetric toSponsorExposure(SponsorBucketMetric metric) {
        Double average = metric.detectionCount() == 0 ? null : round(metric.confidenceSum() / metric.detectionCount());
        return new SponsorExposureMetric(metric.sponsor(), metric.detectionCount(), metric.acceptedDetectionCount(),
                metric.estimatedExposureMs(), average, metric.maxConfidence() == null ? null : round(metric.maxConfidence()),
                metric.fallbackDetectionCount(), metric.lowConfidenceDetectionCount());
    }

    private QueryWindow window(String streamer, String streamSessionId, Integer requestedWindowMinutes,
            Integer requestedBucketSeconds) {
        String cleanedStreamer = cleanRequired(streamer, "streamer");
        int windowMinutes = requestedWindowMinutes == null ? properties.getAnalytics().getDefaultWindowMinutes()
                : requestedWindowMinutes;
        if (windowMinutes < 1 || windowMinutes > properties.getAnalytics().getMaxWindowMinutes()) {
            throw new IllegalArgumentException("windowMinutes must be between 1 and "
                    + properties.getAnalytics().getMaxWindowMinutes());
        }
        int bucketSeconds = requestedBucketSeconds == null ? properties.getAnalytics().getBucketSizeSeconds()
                : requestedBucketSeconds;
        if (bucketSeconds != properties.getAnalytics().getBucketSizeSeconds()) {
            throw new IllegalArgumentException("only bucketSeconds=" + properties.getAnalytics().getBucketSizeSeconds()
                    + " is supported");
        }
        long now = clock.millis();
        long bucketMs = bucketSeconds * 1000L;
        long windowEnd = Math.floorDiv(now, bucketMs) * bucketMs + bucketMs;
        long windowStart = windowEnd - windowMinutes * 60_000L;
        String cleanedSession = clean(streamSessionId);
        return new QueryWindow(cleanedStreamer, cleanedSession, cleanedSession, windowMinutes, bucketSeconds, windowStart,
                windowEnd);
    }

    private Double average(double sum, long count) {
        return count == 0 ? null : round(sum / count);
    }

    private Double ratio(long numerator, long denominator) {
        return denominator == 0 ? null : round((double) numerator / denominator);
    }

    private double value(Double value) {
        return value == null ? 0.0d : value;
    }

    private double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private String riskLevel(double score) {
        if (score >= 0.67d) {
            return "HIGH";
        }
        if (score >= 0.34d) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Double round(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    private String cleanRequired(String value, String name) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record QueryWindow(String streamer, String streamSessionId, String sessionKey, int windowMinutes,
            int bucketSizeSeconds, long windowStart, long windowEnd) {
    }
}
