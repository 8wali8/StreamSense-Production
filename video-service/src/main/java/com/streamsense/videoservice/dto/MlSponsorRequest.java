package com.streamsense.videoservice.dto;

import java.util.List;

/**
 * What ml-engine is asked about a frame: the frame itself and, when the channel's current deal
 * carries a logo, the sponsor to stamp detections with, the deal and first logo ids to record on
 * them, and the logo images to look for.
 */
public record MlSponsorRequest(
        String frameId,
        String streamer,
        String frameRef,
        long frameSequence,
        long capturedAt,
        String source,
        String channelLogin,
        String streamSessionId,
        String twitchStreamId,
        Long videoTimestampMs,
        String artifactContentType,
        Long artifactSizeBytes,
        String sponsor,
        Long dealId,
        Long logoId,
        List<String> logoRefs) {

    public MlSponsorRequest {
        logoRefs = logoRefs == null ? List.of() : List.copyOf(logoRefs);
    }

    public MlSponsorRequest(String frameId, String streamer, String frameRef, long frameSequence, long capturedAt) {
        this(frameId, streamer, frameRef, frameSequence, capturedAt, null, null, null, null, null, null, null);
    }

    public MlSponsorRequest(
            String frameId,
            String streamer,
            String frameRef,
            long frameSequence,
            long capturedAt,
            String source,
            String channelLogin,
            String streamSessionId,
            String twitchStreamId,
            Long videoTimestampMs,
            String artifactContentType,
            Long artifactSizeBytes) {
        this(
                frameId,
                streamer,
                frameRef,
                frameSequence,
                capturedAt,
                source,
                channelLogin,
                streamSessionId,
                twitchStreamId,
                videoTimestampMs,
                artifactContentType,
                artifactSizeBytes,
                null,
                null,
                null,
                List.of());
    }

    /** The same frame, asked about a deal's logo. */
    public MlSponsorRequest forDeal(String sponsor, long dealId, Long logoId, List<String> logoRefs) {
        return new MlSponsorRequest(
                frameId,
                streamer,
                frameRef,
                frameSequence,
                capturedAt,
                source,
                channelLogin,
                streamSessionId,
                twitchStreamId,
                videoTimestampMs,
                artifactContentType,
                artifactSizeBytes,
                sponsor,
                dealId,
                logoId,
                logoRefs);
    }
}
