package com.streamsense.analyticsservice.api;

import com.streamsense.analyticsservice.model.SponsorOutcomeTotals;

/**
 * Whether the session's frames were examined for the sponsor's logo, so a report can say when its
 * on-screen numbers mean nothing. {@code state} is {@code ON} (every frame examined), {@code PARTIAL}
 * (some frames could not be examined; {@code unavailableMs} says how much of the stream), {@code
 * UNAVAILABLE} (none could), {@code OFF} (the deal had no logo, nothing was looked for), or
 * {@code NO_FRAMES} (no video was captured at all). The counts are frames, one per sample interval.
 */
public record OnScreenTracking(
        String state, long examinedFrames, long unavailableFrames, long noLogoFrames, long unavailableMs) {

    public static final String ON = "ON";
    public static final String PARTIAL = "PARTIAL";
    public static final String UNAVAILABLE = "UNAVAILABLE";
    public static final String OFF = "OFF";
    public static final String NO_FRAMES = "NO_FRAMES";

    public static OnScreenTracking of(SponsorOutcomeTotals totals, long exposureMsPerFrame) {
        String state;
        if (totals.frames() == 0) {
            state = NO_FRAMES;
        } else if (totals.examined() == 0 && totals.unavailable() == 0) {
            state = OFF;
        } else if (totals.examined() == 0) {
            state = UNAVAILABLE;
        } else if (totals.unavailable() > 0) {
            state = PARTIAL;
        } else {
            state = ON;
        }
        return new OnScreenTracking(
                state,
                totals.examined(),
                totals.unavailable(),
                totals.noLogo(),
                totals.unavailable() * exposureMsPerFrame);
    }

    /** True when the on-screen numbers rest on frames that were examined: ON or PARTIAL. */
    public boolean logoTracked() {
        return ON.equals(state) || PARTIAL.equals(state);
    }
}
