package com.streamsense.apigateway.analytics;

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
        DirectResponse response) {

    public record SessionValue(
            Double logoValue,
            Double hostReadValue,
            Double mediaValue,
            Double weightedLogoViewerMinutes,
            Double averageProminence,
            double cpmPer30sEquivalent,
            double hostReadRatePer1000,
            String basis) {}

    public record DirectResponse(
            String chatCommand, long commandUses, long commandUsers, String trackedLinkHost, long linkPosts) {}
}
