package com.streamsense.analyticsservice.api;

/**
 * Everything the one-page session report shows, for one session and one sponsor, in one call.
 * {@code dealId} is the deal the session fell inside, when there is one; its rates, command, and
 * link host fill whatever the request left unspecified. {@code onScreenTracking} says whether the
 * frames were examined for the sponsor's logo at all; when they were not, the logo and media values are null.
 */
public record SessionSummary(
        StreamSession session,
        Long dealId,
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
        DirectResponse response,
        OnScreenTracking onScreenTracking) {}
