package com.streamsense.videoservice.persistence;

import com.streamsense.videoservice.events.SponsorDetectionEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sponsor_detections")
public class SponsorDetectionEntity {

    @Id
    @Column(name = "detection_event_id", nullable = false, length = 64)
    private String detectionEventId;

    @Column(name = "source_frame_id", nullable = false, length = 64)
    private String sourceFrameId;

    @Column(name = "streamer", nullable = false, length = 255)
    private String streamer;

    @Column(name = "frame_ref", nullable = false, length = 1024)
    private String frameRef;

    @Column(name = "frame_sequence", nullable = false)
    private long frameSequence;

    @Column(name = "captured_at", nullable = false)
    private long capturedAt;

    @Column(name = "processed_at", nullable = false)
    private long processedAt;

    @Column(name = "sponsor", nullable = false, length = 128)
    private String sponsor;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "box_x", nullable = false)
    private double x;

    @Column(name = "box_y", nullable = false)
    private double y;

    @Column(name = "box_width", nullable = false)
    private double width;

    @Column(name = "box_height", nullable = false)
    private double height;

    @Column(name = "source", length = 32)
    private String source;

    @Column(name = "channel_login", length = 255)
    private String channelLogin;

    @Column(name = "stream_session_id", length = 255)
    private String streamSessionId;

    @Column(name = "twitch_stream_id", length = 128)
    private String twitchStreamId;

    @Column(name = "video_timestamp_ms")
    private Long videoTimestampMs;

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

    public SponsorDetectionEvent toEvent() {
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId(detectionEventId);
        event.setSourceFrameId(sourceFrameId);
        event.setStreamer(streamer);
        event.setFrameRef(frameRef);
        event.setFrameSequence(frameSequence);
        event.setCapturedAt(capturedAt);
        event.setProcessedAt(processedAt);
        event.setSponsor(sponsor);
        event.setConfidence(confidence);
        event.setModelVersion(modelVersion);
        event.setX(x);
        event.setY(y);
        event.setWidth(width);
        event.setHeight(height);
        event.setSource(source);
        event.setChannelLogin(channelLogin);
        event.setStreamSessionId(streamSessionId);
        event.setTwitchStreamId(twitchStreamId);
        event.setVideoTimestampMs(videoTimestampMs);
        return event;
    }

    public static SponsorDetectionEntity fromEvent(SponsorDetectionEvent event) {
        SponsorDetectionEntity entity = new SponsorDetectionEntity();
        entity.setDetectionEventId(event.getDetectionEventId());
        entity.setSourceFrameId(event.getSourceFrameId());
        entity.setStreamer(event.getStreamer());
        entity.setFrameRef(event.getFrameRef());
        entity.setFrameSequence(event.getFrameSequence());
        entity.setCapturedAt(event.getCapturedAt());
        entity.setProcessedAt(event.getProcessedAt());
        entity.setSponsor(event.getSponsor());
        entity.setConfidence(event.getConfidence());
        entity.setModelVersion(event.getModelVersion());
        entity.setX(event.getX());
        entity.setY(event.getY());
        entity.setWidth(event.getWidth());
        entity.setHeight(event.getHeight());
        entity.setSource(event.getSource());
        entity.setChannelLogin(event.getChannelLogin());
        entity.setStreamSessionId(event.getStreamSessionId());
        entity.setTwitchStreamId(event.getTwitchStreamId());
        entity.setVideoTimestampMs(event.getVideoTimestampMs());
        return entity;
    }
}
