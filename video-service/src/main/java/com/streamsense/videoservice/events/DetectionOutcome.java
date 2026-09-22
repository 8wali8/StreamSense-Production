package com.streamsense.videoservice.events;

/**
 * What happened to a sampled frame. Carried on the detection event as a string so consumers built
 * before a value existed keep reading; {@link #resolve} is the one place the older events are read.
 */
public enum DetectionOutcome {
    /** The logo is in the frame, at the box. */
    DETECTED,
    /** The detector looked for the logo and did not find it: zero confidence, zero box. */
    NOT_DETECTED,
    /** Nothing was looked for: the channel has no deal with a logo. */
    NO_LOGO,
    /** The frame could not be examined: ml-engine or the deal lookup was unavailable (the fallback path). */
    UNAVAILABLE;

    /** The outcome an event carries, or, for an event from before outcomes, what its model version implies. */
    public static DetectionOutcome resolve(String outcome, String modelVersion) {
        if (outcome != null && !outcome.isBlank()) {
            return DetectionOutcome.valueOf(outcome.trim());
        }
        return SponsorDetectionEvent.isFallbackModelVersion(modelVersion) ? UNAVAILABLE : DETECTED;
    }

    /** True for the outcomes in which the logo was looked for. */
    public boolean examined() {
        return this == DETECTED || this == NOT_DETECTED;
    }
}
