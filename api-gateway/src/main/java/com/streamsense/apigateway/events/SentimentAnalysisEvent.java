package com.streamsense.apigateway.events;

import java.util.ArrayList;
import java.util.List;

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
    private boolean sponsorRelevant;
    private String matchedSponsor;
    private List<String> matchedTerms = new ArrayList<>();
    private double relevanceScore;
    private String relevanceReason;
    private String relevanceVersion;
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

    public boolean isSponsorRelevant() {
        return sponsorRelevant;
    }

    public void setSponsorRelevant(boolean sponsorRelevant) {
        this.sponsorRelevant = sponsorRelevant;
    }

    public String getMatchedSponsor() {
        return matchedSponsor;
    }

    public void setMatchedSponsor(String matchedSponsor) {
        this.matchedSponsor = matchedSponsor;
    }

    public List<String> getMatchedTerms() {
        return matchedTerms;
    }

    public void setMatchedTerms(List<String> matchedTerms) {
        this.matchedTerms = matchedTerms != null ? matchedTerms : new ArrayList<>();
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public String getRelevanceReason() {
        return relevanceReason;
    }

    public void setRelevanceReason(String relevanceReason) {
        this.relevanceReason = relevanceReason;
    }

    public String getRelevanceVersion() {
        return relevanceVersion;
    }

    public void setRelevanceVersion(String relevanceVersion) {
        this.relevanceVersion = relevanceVersion;
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
