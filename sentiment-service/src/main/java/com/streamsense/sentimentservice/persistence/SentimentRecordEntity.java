package com.streamsense.sentimentservice.persistence;

import org.hibernate.annotations.Immutable;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sentiment_events")
public class SentimentRecordEntity {

    @Id
    @Column(name = "sentiment_event_id", nullable = false, length = 64)
    private String sentimentEventId;

    @Column(name = "source_event_id", nullable = false, length = 64)
    private String sourceEventId;

    @Column(name = "streamer", nullable = false, length = 255)
    private String streamer;

    @Column(name = "user_name", nullable = false, length = 255)
    private String userName;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "chat_timestamp", nullable = false)
    private long chatTimestamp;

    @Column(name = "processed_at", nullable = false)
    private long processedAt;

    @Column(name = "label", nullable = false, length = 32)
    private String label;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "model_version", nullable = false, length = 64)
    private String modelVersion;

    public String getSentimentEventId() {
        return sentimentEventId;
    }

    public void setSentimentEventId(String sentimentEventId) {
        this.sentimentEventId = sentimentEventId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getStreamer() {
        return streamer;
    }

    public void setStreamer(String streamer) {
        this.streamer = streamer;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getChatTimestamp() {
        return chatTimestamp;
    }

    public void setChatTimestamp(long chatTimestamp) {
        this.chatTimestamp = chatTimestamp;
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

    public SentimentAnalysisEvent toEvent() {
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId(sentimentEventId);
        event.setSourceEventId(sourceEventId);
        event.setStreamer(streamer);
        event.setUser(userName);
        event.setMessage(message);
        event.setChatTimestamp(chatTimestamp);
        event.setProcessedAt(processedAt);
        event.setLabel(label);
        event.setScore(score);
        event.setModelVersion(modelVersion);
        return event;
    }

    public static SentimentRecordEntity fromEvent(SentimentAnalysisEvent event) {
        SentimentRecordEntity entity = new SentimentRecordEntity();
        entity.setSentimentEventId(event.getSentimentEventId());
        entity.setSourceEventId(event.getSourceEventId());
        entity.setStreamer(event.getStreamer());
        entity.setUserName(event.getUser());
        entity.setMessage(event.getMessage());
        entity.setChatTimestamp(event.getChatTimestamp());
        entity.setProcessedAt(event.getProcessedAt());
        entity.setLabel(event.getLabel());
        entity.setScore(event.getScore());
        entity.setModelVersion(event.getModelVersion());
        return entity;
    }
}
