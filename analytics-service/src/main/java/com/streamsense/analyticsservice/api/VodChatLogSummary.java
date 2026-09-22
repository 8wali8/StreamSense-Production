package com.streamsense.analyticsservice.api;

/** The chat log supplied for a recording: how many lines, the span they cover, and when it was uploaded. */
public record VodChatLogSummary(
        String fileName, int lineCount, double firstOffsetSeconds, double lastOffsetSeconds, long uploadedAt) {}
