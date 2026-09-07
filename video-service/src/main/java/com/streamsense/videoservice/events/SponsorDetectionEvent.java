package com.streamsense.videoservice.events;

public class SponsorDetectionEvent {

    private String detectionEventId;
    private String sourceFrameId;
    private String streamer;
    private String frameRef;
    private long frameSequence;
    private long capturedAt;
    private long processedAt;
    private String sponsor;
    private double confidence;
    private String modelVersion;
    private double x;
    private double y;
    private double width;
    private double height;
    private String source;
    private String channelLogin;
    private String streamSessionId;
    private String twitchStreamId;
    private Long videoTimestampMs;
    private Boolean fallback;

    public String getDetectionEventId() {
        return detectionEventId;
    }

    public void setDetectionEventId(String detectionEventId) {
        this.detectionEventId = detectionEventId;
    }

    public String getSourceFrameId() {
        return sourceFrameId;
    }

    public void setSourceFrameId(String sourceFrameId) {
        this.sourceFrameId = sourceFrameId;
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

    public long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(long processedAt) {
        this.processedAt = processedAt;
    }

    public String getSponsor() {
        return sponsor;
    }

    public void setSponsor(String sponsor) {
        this.sponsor = sponsor;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
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

    /** The ml-engine fallback path reports this model version; it is the one fact {@code fallback} is derived from. */
    public static final String FALLBACK_MODEL_VERSION = "fallback";

    public static boolean isFallbackModelVersion(String modelVersion) {
        return FALLBACK_MODEL_VERSION.equalsIgnoreCase(modelVersion);
    }

    public Boolean getFallback() {
        return fallback;
    }

    public void setFallback(Boolean fallback) {
        this.fallback = fallback;
    }
}
