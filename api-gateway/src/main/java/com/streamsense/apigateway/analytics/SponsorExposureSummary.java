package com.streamsense.apigateway.analytics;

import java.util.List;

public record SponsorExposureSummary(
        long totalDetections,
        long acceptedDetections,
        long estimatedExposureMs,
        List<SponsorExposureMetric> topSponsors) {}
