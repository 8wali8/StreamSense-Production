package com.streamsense.analyticsservice.web;

public record SponsorExposureMetric(
        String sponsor,
        long detectionCount,
        long acceptedDetectionCount,
        long estimatedExposureMs,
        Double averageConfidence,
        Double maxConfidence,
        long fallbackDetectionCount,
        long lowConfidenceDetectionCount) {}
