package com.streamsense.analyticsservice.api;

public record ChatMetrics(
        long totalMessages, double messagesPerMinute, long uniqueChatters, long peakMessagesPerMinute) {}
