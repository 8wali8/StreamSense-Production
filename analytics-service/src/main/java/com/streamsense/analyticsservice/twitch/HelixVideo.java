package com.streamsense.analyticsservice.twitch;

/** One recording from the Helix videos endpoint (type archive). {@code streamId} may be null. */
public record HelixVideo(
        String id,
        String streamId,
        String userLogin,
        String title,
        long createdAt,
        long durationMs,
        String url,
        long viewCount) {}
