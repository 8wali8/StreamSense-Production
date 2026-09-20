package com.streamsense.analyticsservice.model;

import java.util.Set;

/**
 * A recording's import as analytics-service last recorded it. {@code state} is the whole import
 * (QUEUED, IMPORTING, STOPPING, STOPPED, DONE, FAILED); the two half states are what chat-service and
 * video-capture-service last reported (QUEUED, RUNNING, STOPPED, DONE, FAILED) and where each had
 * reached, so a resume can pick up from the slower one.
 */
public record VodImportRow(
        String streamer,
        String vodId,
        Long sessionId,
        String state,
        long offsetSeconds,
        long durationSeconds,
        String chatState,
        long chatOffsetSeconds,
        String captureState,
        long captureOffsetSeconds,
        String lastError,
        long requestedAt,
        long updatedAt) {

    public static final String QUEUED = "QUEUED";
    public static final String IMPORTING = "IMPORTING";
    public static final String STOPPING = "STOPPING";
    public static final String STOPPED = "STOPPED";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    /** The states in which a service is still working on the import, or has been asked to stop. */
    public static final Set<String> ACTIVE = Set.of(QUEUED, IMPORTING, STOPPING);

    public boolean isActive() {
        return ACTIVE.contains(state);
    }

    /** A stopped or failed import is asked for again from the offset it reached. */
    public boolean isResumable() {
        return STOPPED.equals(state) || FAILED.equals(state);
    }

    public VodImportRow withState(String newState, long now) {
        return new VodImportRow(
                streamer,
                vodId,
                sessionId,
                newState,
                offsetSeconds,
                durationSeconds,
                chatState,
                chatOffsetSeconds,
                captureState,
                captureOffsetSeconds,
                lastError,
                requestedAt,
                now);
    }
}
