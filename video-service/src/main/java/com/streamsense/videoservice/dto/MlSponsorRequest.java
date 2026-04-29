package com.streamsense.videoservice.dto;

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
        Long artifactSizeBytes) {

    public MlSponsorRequest(String frameId, String streamer, String frameRef, long frameSequence, long capturedAt) {
        this(frameId, streamer, frameRef, frameSequence, capturedAt, null, null, null, null, null, null, null);
    }
}
