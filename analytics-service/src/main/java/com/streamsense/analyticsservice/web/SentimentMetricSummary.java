package com.streamsense.analyticsservice.web;

public record SentimentMetricSummary(
        long positive,
        long neutral,
        long negative,
        Double averageScore,
        Double negativeRatio) {
}
