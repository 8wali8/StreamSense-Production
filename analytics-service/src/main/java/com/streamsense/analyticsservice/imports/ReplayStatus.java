package com.streamsense.analyticsservice.imports;

import java.util.Map;

/**
 * What one replay service says about a recording's import: its state (QUEUED, RUNNING, STOPPED,
 * DONE, FAILED), how far into the recording it has got, and whether a stop is pending. Both services
 * answer the same shape for the fields named here.
 */
public record ReplayStatus(String state, long offsetSeconds, boolean stopRequested, String lastError) {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String STOPPED = "STOPPED";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";

    public boolean isActive() {
        return QUEUED.equals(state) || RUNNING.equals(state);
    }

    static ReplayStatus from(Map<String, Object> body) {
        Object state = body.get("state");
        Object offset = body.get("offsetSeconds");
        Object error = body.get("lastError");
        return new ReplayStatus(
                state == null ? FAILED : state.toString(),
                offset instanceof Number number ? (long) Math.floor(number.doubleValue()) : 0L,
                Boolean.TRUE.equals(body.get("stopRequested")),
                error == null ? null : error.toString());
    }
}
