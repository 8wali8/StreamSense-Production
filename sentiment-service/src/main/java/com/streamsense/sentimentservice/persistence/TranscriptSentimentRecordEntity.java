package com.streamsense.sentimentservice.persistence;

import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;

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
        event.setSponsorRelevant(sponsorRelevant);
        event.setMatchedSponsor(matchedSponsor);
        event.setMatchedTerms(splitTerms(matchedTerms));
        event.setRelevanceScore(relevanceScore);
        event.setRelevanceReason(relevanceReason);
        event.setRelevanceVersion(relevanceVersion);
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
