package com.streamsense.sentimentservice.persistence;

import com.streamsense.sentimentservice.events.TranscriptSegmentEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transcript_segments")
public class TranscriptSegmentRecordEntity {

    @Id
    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "streamer", nullable = false, length = 255)
    private String streamer;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;

    @Column(name = "started_at", nullable = false)
    private long startedAt;

    @Column(name = "ended_at", nullable = false)
    private long endedAt;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "model_version", nullable = false, length = 128)
    private String modelVersion;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "channel_login", length = 255)
    private String channelLogin;

    @Column(name = "stream_session_id", nullable = false, length = 255)
    private String streamSessionId;

    @Column(name = "twitch_stream_id", length = 255)
    private String twitchStreamId;

    @Column(name = "video_timestamp_ms", nullable = false)
    private long videoTimestampMs;

    @Column(name = "transcript_sequence", nullable = false)
    private long transcriptSequence;

    @Column(name = "capture_worker_id", length = 255)
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

    public TranscriptSegmentEvent toEvent() {
        TranscriptSegmentEvent event = new TranscriptSegmentEvent();
        event.setSegmentId(segmentId);
        event.setStreamer(streamer);
        event.setText(text);
        event.setStartedAt(startedAt);
        event.setEndedAt(endedAt);
        event.setLanguage(language);
        event.setConfidence(confidence);
        event.setModelVersion(modelVersion);
        event.setSource(source);
        event.setChannelLogin(channelLogin);
        event.setStreamSessionId(streamSessionId);
        event.setTwitchStreamId(twitchStreamId);
        event.setVideoTimestampMs(videoTimestampMs);
        event.setTranscriptSequence(transcriptSequence);
        event.setCaptureWorkerId(captureWorkerId);
        return event;
    }

    public static TranscriptSegmentRecordEntity fromEvent(TranscriptSegmentEvent event) {
        TranscriptSegmentRecordEntity entity = new TranscriptSegmentRecordEntity();
        entity.setSegmentId(event.getSegmentId());
        entity.setStreamer(event.getStreamer());
        entity.setText(event.getText());
        entity.setStartedAt(event.getStartedAt());
        entity.setEndedAt(event.getEndedAt());
        entity.setLanguage(event.getLanguage());
        entity.setConfidence(event.getConfidence());
        entity.setModelVersion(event.getModelVersion());
        entity.setSource(event.getSource());
        entity.setChannelLogin(event.getChannelLogin());
        entity.setStreamSessionId(event.getStreamSessionId());
        entity.setTwitchStreamId(event.getTwitchStreamId());
        entity.setVideoTimestampMs(event.getVideoTimestampMs());
        entity.setTranscriptSequence(event.getTranscriptSequence());
        entity.setCaptureWorkerId(event.getCaptureWorkerId());
        return entity;
    }
}
