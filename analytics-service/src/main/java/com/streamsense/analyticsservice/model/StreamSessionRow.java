package com.streamsense.analyticsservice.model;

/** A stream_sessions row as stored. */
public record StreamSessionRow(
        long id,
        String streamer,
        String source,
        String sessionRef,
        String twitchStreamId,
        String streamSessionId,
        String channelLogin,
        String title,
        String category,
        long startedAt,
        Long endedAt,
        long lastSeenAt,
        Integer peakViewers,
        long viewerSum,
        int viewerSamples,
        String vodId) {

    public boolean isOpen() {
        return endedAt == null;
    }

    /** The session's end for overlap arithmetic: its close time, or "now" while it is open. */
    public long endOr(long now) {
        return endedAt == null ? now : endedAt;
    }
}
