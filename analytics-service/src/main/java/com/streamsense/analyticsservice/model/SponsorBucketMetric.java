package com.streamsense.analyticsservice.model;

public record SponsorBucketMetric(
        String sponsor,
        long detectionCount,
        long acceptedDetectionCount,
        long lowConfidenceDetectionCount,
        long fallbackDetectionCount,
        long estimatedExposureMs,
        double confidenceSum,
        Double maxConfidence) {
}
