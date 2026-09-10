package com.streamsense.apigateway.analytics;

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
        int viewerSamples,
        String vodId) {}
