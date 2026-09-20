package com.streamsense.analyticsservice.model;

/** What is known about the chat log supplied for a recording, without its lines. */
public record VodChatLogRow(
        String streamer,
        String vodId,
        String fileName,
        int lineCount,
        double firstOffsetSeconds,
        double lastOffsetSeconds,
        long uploadedAt) {}
