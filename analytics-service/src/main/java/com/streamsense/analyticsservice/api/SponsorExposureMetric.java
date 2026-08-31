package com.streamsense.analyticsservice.api;

public record SponsorExposureMetric(
        String sponsor,
        long detectionCount,
        long acceptedDetectionCount,
        long estimatedExposureMs,
        Double averageConfidence,
        Double maxConfidence,
        long fallbackDetectionCount,
        long lowConfidenceDetectionCount) {}
