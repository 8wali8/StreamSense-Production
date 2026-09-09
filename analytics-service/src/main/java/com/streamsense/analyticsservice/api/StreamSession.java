package com.streamsense.analyticsservice.api;

/**
 * One stream as the API reports it. {@code source} is HELIX when the Twitch poller saw the
 * broadcast and CAPTURE when the session was derived from captured events; {@code live} is true
 * while no end has been recorded. Viewer figures are null until the poller has sampled them.
 */
public record StreamSession(
        long id,
        String streamer,
        String source,
        String twitchStreamId,
        String streamSessionId,
        String channelLogin,
        String title,
        String category,
        long startedAt,
        Long endedAt,
        boolean live,
        long durationMs,
        Integer peakViewers,
        Double averageViewers,
        int viewerSamples) {}
