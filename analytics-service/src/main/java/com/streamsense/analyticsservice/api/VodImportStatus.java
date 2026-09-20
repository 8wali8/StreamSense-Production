package com.streamsense.analyticsservice.api;

/**
 * Where a recording's import stands, as analytics-service last recorded it. {@code state} is the
 * import as a whole: QUEUED, IMPORTING, STOPPING (a stop was asked for and one half has yet to
 * confirm), STOPPED, DONE, or FAILED. {@code offsetSeconds} is how far into the recording both halves
 * have got, so it follows the slower one; a resume continues from there. The two half states are what
 * chat-service and video-capture-service last reported.
 */
public record VodImportStatus(
        String streamer,
        String vodId,
        Long sessionId,
        String state,
        long offsetSeconds,
        long durationSeconds,
        String chatState,
        String captureState,
        String lastError,
        long updatedAt) {}
