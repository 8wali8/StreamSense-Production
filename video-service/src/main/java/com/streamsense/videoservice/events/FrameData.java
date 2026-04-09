package com.streamsense.videoservice.events;

public class FrameData {

    private String frameId;
    private String streamer;
    private String frameRef;
    private long frameSequence;
    private long capturedAt;

    public String getFrameId() {
        return frameId;
    }

    public void setFrameId(String frameId) {
        this.frameId = frameId;
    }

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
