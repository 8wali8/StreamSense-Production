package com.streamsense.videoservice.dto;

public record MlSponsorRequest(
        String frameId,
        String streamer,
        String frameRef,
        long frameSequence,
        long capturedAt) {
}
