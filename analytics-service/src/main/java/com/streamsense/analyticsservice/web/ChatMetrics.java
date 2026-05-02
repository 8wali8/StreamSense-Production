package com.streamsense.analyticsservice.web;

public record ChatMetrics(long totalMessages, double messagesPerMinute, long uniqueChatters, long peakMessagesPerMinute) {
}
