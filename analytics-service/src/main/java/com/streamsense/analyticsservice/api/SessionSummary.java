package com.streamsense.analyticsservice.api;

/** Everything the one-page session report shows, for one session and one sponsor, in one call. */
public record SessionSummary(
        StreamSession session,
        String sponsor,
        long onScreenMs,
        Double onScreenShare,
        long mentions,
        long chatMentions,
        long voiceMentions,
        Double mentionSentiment,
        Double mentionPositiveShare,
        Double mentionNegativeShare,
        Double averageViewers,
        Integer peakViewers,
        BrandSafetyMetrics risk,
        ChatMetrics chat,
        SentimentMetricSummary chatSentiment,
        SentimentMetricSummary transcriptSentiment,
        EngagementMetrics engagement,
        SessionValue value,
        DirectResponse response) {}
