package com.streamsense.analyticsservice.api;

/**
 * One past broadcast Twitch still has the recording of, with the session it was imported into when
 * that has happened. {@code streamId} is the broadcast's Helix stream id when Twitch reports one.
 */
public record VodListing(
        String vodId,
        String streamId,
        String title,
        long createdAt,
        long durationMs,
        String url,
        long viewCount,
        Long sessionId) {}
