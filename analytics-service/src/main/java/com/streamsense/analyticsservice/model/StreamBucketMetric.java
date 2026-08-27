package com.streamsense.analyticsservice.model;

public record StreamBucketMetric(
        long bucketStart,
        int bucketSizeSeconds,
        long chatMessageCount,
        long uniqueChatters,
        long chatSentimentCount,
        long chatPositiveCount,
        long chatNeutralCount,
        long chatNegativeCount,
        double chatScoreSum,
        long transcriptSentimentCount,
        long transcriptPositiveCount,
        long transcriptNeutralCount,
        long transcriptNegativeCount,
        double transcriptScoreSum,
        long negativeSpikeCount,
        long engagementSpikeCount) {
}
