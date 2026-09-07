package com.streamsense.analyticsservice.api;

public record SentimentMetricSummary(
        long positive, long neutral, long negative, Double averageScore, Double negativeRatio) {}
