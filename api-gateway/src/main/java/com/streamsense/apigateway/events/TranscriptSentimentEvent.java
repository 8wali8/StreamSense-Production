package com.streamsense.apigateway.events;

import java.util.ArrayList;
import java.util.List;

public class TranscriptSentimentEvent {

    private String sentimentEventId;
    private String segmentId;
    private String streamer;
    private String text;
    private long segmentStartedAt;
    private long segmentEndedAt;
    private long processedAt;
    private String label;
    private double score;
    private String modelVersion;
    private String transcriptModelVersion;
    private String streamSessionId;
    private long transcriptSequence;
    private boolean sponsorRelevant;
    private String matchedSponsor;
    private List<String> matchedTerms = new ArrayList<>();
    private double relevanceScore;
    private String relevanceReason;
    private String relevanceVersion;

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
}
