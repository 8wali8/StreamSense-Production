package com.streamsense.analyticsservice.web;

public record AnalyticsDataQuality(boolean lowData, Long latestEventAt, Long aggregationLagMs) {}
