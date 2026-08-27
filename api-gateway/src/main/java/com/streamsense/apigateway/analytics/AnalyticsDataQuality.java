package com.streamsense.apigateway.analytics;

public record AnalyticsDataQuality(boolean lowData, Long latestEventAt, Long aggregationLagMs) {
}
