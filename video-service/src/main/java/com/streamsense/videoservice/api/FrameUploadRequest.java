package com.streamsense.videoservice.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class FrameUploadRequest {

    @NotBlank
    @Size(max = 255)
    private String streamer;

    @NotBlank
    @Size(max = 1024)
    private String frameRef;

    @Min(0)
    private long frameSequence;

    @Positive
    private long capturedAt;

    public String getStreamer() {
        return streamer;
    }

    public void setStreamer(String streamer) {
        this.streamer = streamer;
    }

    public String getFrameRef() {
        return frameRef;
    }

    public void setFrameRef(String frameRef) {
        this.frameRef = frameRef;
    }

    public long getFrameSequence() {
        return frameSequence;
    }

    public void setFrameSequence(long frameSequence) {
        this.frameSequence = frameSequence;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(long capturedAt) {
        this.capturedAt = capturedAt;
    }
}
