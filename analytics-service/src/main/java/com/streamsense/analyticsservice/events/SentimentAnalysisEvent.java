package com.streamsense.analyticsservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SentimentAnalysisEvent {
    private String sentimentEventId;
    private String sourceEventId;
    private String streamer;
    private String user;
    private String message;
    private long chatTimestamp;
    private long processedAt;
    private String label;
    private double score;
    private String modelVersion;
    private String source;
    private String channelLogin;
    private String streamSessionId;
    private String twitchStreamId;

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
}
