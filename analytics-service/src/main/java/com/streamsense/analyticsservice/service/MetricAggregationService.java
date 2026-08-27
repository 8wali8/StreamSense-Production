package com.streamsense.analyticsservice.service;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.events.SentimentAnalysisEvent;
import com.streamsense.analyticsservice.events.SponsorDetectionEvent;
import com.streamsense.analyticsservice.events.TranscriptSentimentEvent;
import com.streamsense.analyticsservice.metrics.AnalyticsMetrics;
import com.streamsense.analyticsservice.persistence.MetricBucketRepository;
import com.streamsense.analyticsservice.persistence.ProcessedEventRepository;
import com.streamsense.analyticsservice.persistence.SponsorMetricBucketRepository;

@Service
public class MetricAggregationService {

    private final StreamSenseProperties properties;
    private final ProcessedEventRepository processedEvents;
    private final MetricBucketRepository metricBuckets;
    private final SponsorMetricBucketRepository sponsorBuckets;
    private final AnalyticsMetrics metrics;
    private final Clock clock;

    @Autowired
    public MetricAggregationService(StreamSenseProperties properties, ProcessedEventRepository processedEvents,
            MetricBucketRepository metricBuckets, SponsorMetricBucketRepository sponsorBuckets, AnalyticsMetrics metrics) {
        this(properties, processedEvents, metricBuckets, sponsorBuckets, metrics, Clock.systemUTC());
    }

    MetricAggregationService(StreamSenseProperties properties, ProcessedEventRepository processedEvents,
            MetricBucketRepository metricBuckets, SponsorMetricBucketRepository sponsorBuckets, AnalyticsMetrics metrics,
            Clock clock) {
        this.properties = properties;
        this.processedEvents = processedEvents;
        this.metricBuckets = metricBuckets;
        this.sponsorBuckets = sponsorBuckets;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public boolean aggregateChatMessage(String topic, ChatMessageEvent event) {
        validate(event.getEventId(), event.getStreamer(), event.getTimestamp(), "chat message");
        String streamer = normalize(event.getStreamer());
        String sessionKey = sessionKey(event.getStreamSessionId(), streamer);
        long now = clock.millis();
        if (!processedEvents.tryMarkProcessed(topic, event.getEventId(), streamer, clean(event.getStreamSessionId()),
                event.getTimestamp(), now)) {
            metrics.eventDuplicate(topic);
            return false;
        }

        long bucketStart = bucketStart(event.getTimestamp());
        metricBuckets.incrementChatMessage(streamer, clean(event.getChannelLogin()), clean(event.getStreamSessionId()),
                sessionKey, clean(event.getTwitchStreamId()), bucketStart, bucketSizeSeconds(), now);
        metricBuckets.insertChatter(streamer, sessionKey, bucketStart, bucketSizeSeconds(), event.getUser(), event.getTimestamp());
        recomputeSpikeFlags(streamer, sessionKey, bucketStart, now);
        metrics.bucketUpdated("chat");
        metrics.eventProcessed(topic);
        metrics.recordLag(topic, now - event.getTimestamp());
        return true;
    }

    @Transactional
    public boolean aggregateChatSentiment(String topic, SentimentAnalysisEvent event) {
        validate(event.getSentimentEventId(), event.getStreamer(), event.getChatTimestamp(), "chat sentiment");
        String streamer = normalize(event.getStreamer());
        String sessionKey = sessionKey(event.getStreamSessionId(), streamer);
        long now = clock.millis();
        if (!processedEvents.tryMarkProcessed(topic, event.getSentimentEventId(), streamer, clean(event.getStreamSessionId()),
                event.getChatTimestamp(), now)) {
            metrics.eventDuplicate(topic);
            return false;
        }

        long bucketStart = bucketStart(event.getChatTimestamp());
        metricBuckets.incrementChatSentiment(streamer, clean(event.getChannelLogin()), clean(event.getStreamSessionId()),
                sessionKey, clean(event.getTwitchStreamId()), bucketStart, bucketSizeSeconds(), event.getLabel(),
                event.getScore(), now);
        recomputeSpikeFlags(streamer, sessionKey, bucketStart, now);
        metrics.bucketUpdated("chat_sentiment");
        metrics.eventProcessed(topic);
        metrics.recordLag(topic, now - event.getChatTimestamp());
        return true;
    }

    @Transactional
    public boolean aggregateTranscriptSentiment(String topic, TranscriptSentimentEvent event) {
        validate(event.getSentimentEventId(), event.getStreamer(), event.getSegmentStartedAt(), "transcript sentiment");
        String streamer = normalize(event.getStreamer());
        String sessionKey = sessionKey(event.getStreamSessionId(), streamer);
        long now = clock.millis();
        if (!processedEvents.tryMarkProcessed(topic, event.getSentimentEventId(), streamer, clean(event.getStreamSessionId()),
                event.getSegmentStartedAt(), now)) {
            metrics.eventDuplicate(topic);
            return false;
        }

        long bucketStart = bucketStart(event.getSegmentStartedAt());
        metricBuckets.incrementTranscriptSentiment(streamer, clean(event.getStreamSessionId()), sessionKey, bucketStart,
                bucketSizeSeconds(), event.getLabel(), event.getScore(), now);
        metrics.bucketUpdated("transcript_sentiment");
        metrics.eventProcessed(topic);
        metrics.recordLag(topic, now - event.getSegmentStartedAt());
        return true;
    }

    @Transactional
    public boolean aggregateSponsorDetection(String topic, SponsorDetectionEvent event) {
        validate(event.getDetectionEventId(), event.getStreamer(), event.getCapturedAt(), "sponsor detection");
        String streamer = normalize(event.getStreamer());
        String sessionKey = sessionKey(event.getStreamSessionId(), streamer);
        long now = clock.millis();
        if (!processedEvents.tryMarkProcessed(topic, event.getDetectionEventId(), streamer, clean(event.getStreamSessionId()),
                event.getCapturedAt(), now)) {
            metrics.eventDuplicate(topic);
            return false;
        }

        long bucketStart = bucketStart(event.getCapturedAt());
        boolean accepted = event.getConfidence() >= properties.getAnalytics().getMinimumSponsorConfidence();
        boolean fallback = Boolean.TRUE.equals(event.getFallback()) || "fallback".equalsIgnoreCase(event.getModelVersion());
        sponsorBuckets.incrementSponsor(streamer, clean(event.getChannelLogin()), clean(event.getStreamSessionId()),
                sessionKey, clean(event.getTwitchStreamId()), bucketStart, bucketSizeSeconds(), event.getSponsor(),
                event.getConfidence(), accepted, fallback,
                properties.getAnalytics().getEstimatedSponsorExposureMsPerDetection(), now);
        metrics.bucketUpdated("sponsor");
        metrics.eventProcessed(topic);
        metrics.recordLag(topic, now - event.getCapturedAt());
        return true;
    }

    private void recomputeSpikeFlags(String streamer, String sessionKey, long bucketStart, long now) {
        var buckets = metricBuckets.findBuckets(streamer, sessionKey,
                bucketStart - properties.getAnalytics().getEngagementSpikeTrailingWindowMinutes() * 60_000L,
                bucketStart + bucketSizeSeconds() * 1000L, bucketSizeSeconds());
        var current = buckets.stream().filter(bucket -> bucket.bucketStart() == bucketStart).findFirst();
        if (current.isEmpty()) {
            return;
        }
        long negativeCount = current.get().chatNegativeCount();
        long sentimentCount = current.get().chatSentimentCount();
        boolean negativeSpike = sentimentCount >= properties.getAnalytics().getNegativeSpikeMinimumEvents()
                && sentimentCount > 0
                && (double) negativeCount / sentimentCount >= properties.getAnalytics().getNegativeSpikeRatioThreshold();

        double trailingAverage = buckets.stream()
                .filter(bucket -> bucket.bucketStart() < bucketStart)
                .mapToLong(bucket -> bucket.chatMessageCount())
                .average()
                .orElse(0.0d);
        boolean engagementSpike = current.get().chatMessageCount() >= properties.getAnalytics().getEngagementSpikeMinimumMessages()
                && (trailingAverage == 0.0d
                        || current.get().chatMessageCount() >= trailingAverage * properties.getAnalytics().getEngagementSpikeMultiplier());
        metricBuckets.setSpikeFlags(streamer, sessionKey, bucketStart, bucketSizeSeconds(), negativeSpike, engagementSpike, now);
    }

    private long bucketStart(long timestamp) {
        long bucketMs = bucketSizeSeconds() * 1000L;
        return Math.floorDiv(timestamp, bucketMs) * bucketMs;
    }

    private int bucketSizeSeconds() {
        return properties.getAnalytics().getBucketSizeSeconds();
    }

    private String sessionKey(String streamSessionId, String streamer) {
        String cleaned = clean(streamSessionId);
        return cleaned == null ? streamer : cleaned;
    }

    private String normalize(String value) {
        String cleaned = clean(value);
        if (cleaned == null) {
            throw new IllegalArgumentException("streamer is required");
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validate(String eventId, String streamer, long timestamp, String type) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException(type + " eventId is required");
        }
        if (streamer == null || streamer.isBlank()) {
            throw new IllegalArgumentException(type + " streamer is required");
        }
        if (timestamp <= 0) {
            throw new IllegalArgumentException(type + " timestamp must be positive");
        }
    }
}
