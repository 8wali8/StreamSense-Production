package com.streamsense.analyticsservice.api;

public record StreamMetricsSummary(
        String streamer,
        String streamSessionId,
        int windowMinutes,
        int bucketSizeSeconds,
        long windowStart,
        long windowEnd,
        ChatMetrics chat,
        SentimentMetricSummary chatSentiment,
        SentimentMetricSummary transcriptSentiment,
        SponsorExposureSummary sponsorExposure,
        EngagementMetrics engagement,
        BrandSafetyMetrics risk,
        AnalyticsDataQuality dataQuality) {}
