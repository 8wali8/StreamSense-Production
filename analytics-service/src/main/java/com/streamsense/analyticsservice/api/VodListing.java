package com.streamsense.analyticsservice.api;

/**
 * One past broadcast Twitch still has the recording of, with the session it was imported into when
 * that has happened and the chat log supplied for it, if any. {@code streamId} is the broadcast's
 * Helix stream id when Twitch reports one.
 */
public record VodListing(
        String vodId,
        String streamId,
        String title,
        long createdAt,
        long durationMs,
        String url,
        long viewCount,
        Long sessionId,
        VodChatLogSummary chatLog) {}
