package com.streamsense.chatservice.events;

public class SentimentAnalysisEvent {

    private String sentimentEventId;
    private String sourceEventId;
    private String streamer;
    private String user;
    private String message;
    private long timestamp;
    private long processedAt;
    private String label;
    private double score;
    private String modelVersion;

    public SentimentAnalysisEvent() {
    }

    public SentimentAnalysisEvent(
            String sentimentEventId,
            String sourceEventId,
            String streamer,
            String user,
            String message,
            long timestamp,
            long processedAt,
            String label,
            double score,
            String modelVersion) {
        this.sentimentEventId = sentimentEventId;
        this.sourceEventId = sourceEventId;
        this.streamer = streamer;
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
        this.processedAt = processedAt;
        this.label = label;
        this.score = score;
        this.modelVersion = modelVersion;
    }

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

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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

    @Override
    public String toString() {
        return "SentimentAnalysisEvent{" +
                "sentimentEventId='" + sentimentEventId + '\'' +
                ", sourceEventId='" + sourceEventId + '\'' +
                ", streamer='" + streamer + '\'' +
                ", user='" + user + '\'' +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                ", processedAt=" + processedAt +
                ", label='" + label + '\'' +
                ", score=" + score +
                ", modelVersion='" + modelVersion + '\'' +
                '}';
    }
}