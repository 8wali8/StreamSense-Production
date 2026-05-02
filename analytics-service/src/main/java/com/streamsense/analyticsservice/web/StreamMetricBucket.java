package com.streamsense.analyticsservice.web;

public record StreamMetricBucket(
        long bucketStart,
        long bucketEnd,
        long chatMessageCount,
        long uniqueChatters,
        Double chatAverageScore,
        Double chatNegativeRatio,
        Double transcriptAverageScore,
        Double transcriptNegativeRatio,
        long sponsorDetectionCount,
        long estimatedSponsorExposureMs,
        boolean engagementSpike,
        boolean negativeSpike) {
}
