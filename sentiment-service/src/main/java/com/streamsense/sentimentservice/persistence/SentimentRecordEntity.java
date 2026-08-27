package com.streamsense.sentimentservice.persistence;

import java.util.Arrays;
import java.util.List;

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

    @Column(name = "sponsor_relevant", nullable = false)
    private boolean sponsorRelevant;

    @Column(name = "matched_sponsor", length = 255)
    private String matchedSponsor;

    @Column(name = "matched_terms", length = 1000)
    private String matchedTerms;

    @Column(name = "relevance_score", nullable = false)
    private double relevanceScore;

    @Column(name = "relevance_reason", length = 255)
    private String relevanceReason;

    @Column(name = "relevance_version", length = 128)
    private String relevanceVersion;

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

    public String getMatchedTerms() {
        return matchedTerms;
    }

    public void setMatchedTerms(String matchedTerms) {
        this.matchedTerms = matchedTerms;
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
        event.setSponsorRelevant(sponsorRelevant);
        event.setMatchedSponsor(matchedSponsor);
        event.setMatchedTerms(splitTerms(matchedTerms));
        event.setRelevanceScore(relevanceScore);
        event.setRelevanceReason(relevanceReason);
        event.setRelevanceVersion(relevanceVersion);
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
        entity.setSponsorRelevant(event.isSponsorRelevant());
        entity.setMatchedSponsor(event.getMatchedSponsor());
        entity.setMatchedTerms(joinTerms(event.getMatchedTerms()));
        entity.setRelevanceScore(event.getRelevanceScore());
        entity.setRelevanceReason(event.getRelevanceReason());
        entity.setRelevanceVersion(event.getRelevanceVersion());
        return entity;
    }

    private static String joinTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return null;
        }
        return String.join(",", terms);
    }

    private static List<String> splitTerms(String terms) {
        if (terms == null || terms.isBlank()) {
            return List.of();
        }
        return Arrays.stream(terms.split(","))
                .map(String::trim)
                .filter(term -> !term.isBlank())
                .toList();
    }
}
