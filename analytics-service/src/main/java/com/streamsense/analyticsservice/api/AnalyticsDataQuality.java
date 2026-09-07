package com.streamsense.analyticsservice.api;

public record AnalyticsDataQuality(boolean lowData, Long latestEventAt, Long aggregationLagMs) {}
