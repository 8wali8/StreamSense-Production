package com.streamsense.analyticsservice.api;

import java.util.List;

/**
 * The outcome of asking for a VOD import: the session the events will land in, whether the chat and
 * capture replays were started, and anything that went wrong on the way. The events arrive over the
 * following minutes; the session's numbers fill in as they do.
 */
public record VodImport(
        StreamSession session,
        String streamSessionId,
        boolean chatReplayStarted,
        boolean captureReplayStarted,
        List<String> problems) {}
