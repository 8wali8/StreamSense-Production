package com.streamsense.analyticsservice.model;

public record SponsorBucketTotals(
        long bucketStart,
        long detectionCount,
        long estimatedExposureMs) {
}
