package com.streamsense.sentimentservice.events;

public class TranscriptSegmentEvent {

    private String segmentId;
    private String streamer;
    private String text;
    private long startedAt;
    private long endedAt;
    private String language;
    private Double confidence;
    private String modelVersion;
    private String source;
    private String channelLogin;
    private String streamSessionId;
    private String twitchStreamId;
    private long videoTimestampMs;
    private long transcriptSequence;
    private String captureWorkerId;

    public String getSegmentId() {
        return segmentId;
    }

    public void setSegmentId(String segmentId) {
        this.segmentId = segmentId;
    }

    public String getStreamer() {
        return streamer;
    }

    public void setStreamer(String streamer) {
        this.streamer = streamer;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(long endedAt) {
        this.endedAt = endedAt;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
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

    public long getVideoTimestampMs() {
        return videoTimestampMs;
    }

    public void setVideoTimestampMs(long videoTimestampMs) {
        this.videoTimestampMs = videoTimestampMs;
    }

    public long getTranscriptSequence() {
        return transcriptSequence;
    }

    public void setTranscriptSequence(long transcriptSequence) {
        this.transcriptSequence = transcriptSequence;
    }

    public String getCaptureWorkerId() {
        return captureWorkerId;
    }

    public void setCaptureWorkerId(String captureWorkerId) {
        this.captureWorkerId = captureWorkerId;
    }
}
