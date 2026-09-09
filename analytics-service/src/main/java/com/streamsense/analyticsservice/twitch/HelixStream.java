package com.streamsense.analyticsservice.twitch;

/** One live stream from the Helix streams endpoint. {@code startedAt} is epoch millis. */
public record HelixStream(
        String id, String userLogin, String title, String gameName, int viewerCount, long startedAt) {}
