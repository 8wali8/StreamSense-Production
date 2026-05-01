package com.streamsense.sentimentservice.persistence;

import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transcript_sentiment_events")
public class TranscriptSentimentRecordEntity {

    @Id
    @Column(name = "sentiment_event_id", nullable = false, length = 64)
    private String sentimentEventId;

    @Column(name = "segment_id", nullable = false, length = 64)
    private String segmentId;

    @Column(name = "streamer", nullable = false, length = 255)
    private String streamer;

    @Column(name = "text", nullable = false, length = 4000)
    private String text;

    @Column(name = "segment_started_at", nullable = false)
    private long segmentStartedAt;

    @Column(name = "segment_ended_at", nullable = false)
    private long segmentEndedAt;

    @Column(name = "processed_at", nullable = false)
    private long processedAt;

    @Column(name = "label", nullable = false, length = 32)
    private String label;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    @Column(name = "transcript_model_version", nullable = false, length = 128)
    private String transcriptModelVersion;

    @Column(name = "stream_session_id", nullable = false, length = 255)
    private String streamSessionId;

    @Column(name = "transcript_sequence", nullable = false)
    private long transcriptSequence;

    public String getSentimentEventId() {
        return sentimentEventId;
    }

    public void setSentimentEventId(String sentimentEventId) {
        this.sentimentEventId = sentimentEventId;
    }

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

    public long getSegmentStartedAt() {
        return segmentStartedAt;
    }

    public void setSegmentStartedAt(long segmentStartedAt) {
        this.segmentStartedAt = segmentStartedAt;
    }

    public long getSegmentEndedAt() {
        return segmentEndedAt;
    }

    public void setSegmentEndedAt(long segmentEndedAt) {
        this.segmentEndedAt = segmentEndedAt;
    }

    public long getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(long processedAt) {
        this.processedAt = processedAt;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getTranscriptModelVersion() {
        return transcriptModelVersion;
    }

    public void setTranscriptModelVersion(String transcriptModelVersion) {
        this.transcriptModelVersion = transcriptModelVersion;
    }

    public String getStreamSessionId() {
        return streamSessionId;
    }

    public void setStreamSessionId(String streamSessionId) {
        this.streamSessionId = streamSessionId;
    }

    public long getTranscriptSequence() {
        return transcriptSequence;
    }

    public void setTranscriptSequence(long transcriptSequence) {
        this.transcriptSequence = transcriptSequence;
    }

    public TranscriptSentimentEvent toEvent() {
        TranscriptSentimentEvent event = new TranscriptSentimentEvent();
        event.setSentimentEventId(sentimentEventId);
        event.setSegmentId(segmentId);
        event.setStreamer(streamer);
        event.setText(text);
        event.setSegmentStartedAt(segmentStartedAt);
        event.setSegmentEndedAt(segmentEndedAt);
        event.setProcessedAt(processedAt);
        event.setLabel(label);
        event.setScore(score);
        event.setModelVersion(modelVersion);
        event.setTranscriptModelVersion(transcriptModelVersion);
        event.setStreamSessionId(streamSessionId);
        event.setTranscriptSequence(transcriptSequence);
        return event;
    }

    public static TranscriptSentimentRecordEntity fromEvent(TranscriptSentimentEvent event) {
        TranscriptSentimentRecordEntity entity = new TranscriptSentimentRecordEntity();
        entity.setSentimentEventId(event.getSentimentEventId());
        entity.setSegmentId(event.getSegmentId());
        entity.setStreamer(event.getStreamer());
        entity.setText(event.getText());
        entity.setSegmentStartedAt(event.getSegmentStartedAt());
        entity.setSegmentEndedAt(event.getSegmentEndedAt());
        entity.setProcessedAt(event.getProcessedAt());
        entity.setLabel(event.getLabel());
        entity.setScore(event.getScore());
        entity.setModelVersion(event.getModelVersion());
        entity.setTranscriptModelVersion(event.getTranscriptModelVersion());
        entity.setStreamSessionId(event.getStreamSessionId());
        entity.setTranscriptSequence(event.getTranscriptSequence());
        return entity;
    }
}
