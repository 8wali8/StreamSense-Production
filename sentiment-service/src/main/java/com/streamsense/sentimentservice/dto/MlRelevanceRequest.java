package com.streamsense.sentimentservice.dto;

import java.util.ArrayList;
import java.util.List;

public class MlRelevanceRequest {

    private String eventId;
    private String streamer;
    private String text;
    private String sponsor;
    private List<String> aliases = new ArrayList<>();
    private List<String> semanticTerms = new ArrayList<>();
    private Double minScore;

    public MlRelevanceRequest() {}

    public MlRelevanceRequest(
            String eventId,
            String streamer,
            String text,
            String sponsor,
            List<String> aliases,
            List<String> semanticTerms,
            Double minScore) {
        this.eventId = eventId;
        this.streamer = streamer;
        this.text = text;
        this.sponsor = sponsor;
        this.aliases = aliases != null ? aliases : new ArrayList<>();
        this.semanticTerms = semanticTerms != null ? semanticTerms : new ArrayList<>();
        this.minScore = minScore;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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

    public String getSponsor() {
        return sponsor;
    }

    public void setSponsor(String sponsor) {
        this.sponsor = sponsor;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases != null ? aliases : new ArrayList<>();
    }

    public List<String> getSemanticTerms() {
        return semanticTerms;
    }

    public void setSemanticTerms(List<String> semanticTerms) {
        this.semanticTerms = semanticTerms != null ? semanticTerms : new ArrayList<>();
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }
}
