package com.streamsense.apigateway.analytics;

public record ChatMetrics(
        long totalMessages, double messagesPerMinute, long uniqueChatters, long peakMessagesPerMinute) {}
