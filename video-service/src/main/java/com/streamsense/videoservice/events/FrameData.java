package com.streamsense.videoservice.events;

public class FrameData {

    private String frameId;
    private String streamer;
    private String frameRef;
    private long frameSequence;
    private long capturedAt;
    private String source;
    private String channelLogin;
    private String streamSessionId;
    private String twitchStreamId;
    private Long videoTimestampMs;
    private String artifactContentType;
    private Long artifactSizeBytes;
    private String captureWorkerId;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getChannelLogin() {
        return channelLogin;
    }

    public void setChannelLogin(String channelLogin) {
        this.channelLogin = channelLogin;
    }

    public String getStreamSessionId() {
        return streamSessionId;
    }

    public void setStreamSessionId(String streamSessionId) {
        this.streamSessionId = streamSessionId;
    }

    public String getTwitchStreamId() {
        return twitchStreamId;
    }

    public void setTwitchStreamId(String twitchStreamId) {
        this.twitchStreamId = twitchStreamId;
    }

    public Long getVideoTimestampMs() {
        return videoTimestampMs;
    }

    public void setVideoTimestampMs(Long videoTimestampMs) {
        this.videoTimestampMs = videoTimestampMs;
    }

    public String getArtifactContentType() {
        return artifactContentType;
    }

    public void setArtifactContentType(String artifactContentType) {
        this.artifactContentType = artifactContentType;
    }

    public Long getArtifactSizeBytes() {
        return artifactSizeBytes;
    }

    public void setArtifactSizeBytes(Long artifactSizeBytes) {
        this.artifactSizeBytes = artifactSizeBytes;
    }

    public String getCaptureWorkerId() {
        return captureWorkerId;
    }

    public void setCaptureWorkerId(String captureWorkerId) {
        this.captureWorkerId = captureWorkerId;
    }
}
