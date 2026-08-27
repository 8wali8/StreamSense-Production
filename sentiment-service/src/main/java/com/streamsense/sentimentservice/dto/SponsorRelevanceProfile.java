package com.streamsense.sentimentservice.dto;

import java.util.ArrayList;
import java.util.List;

public class SponsorRelevanceProfile {

    private String streamer;
    private String sponsor;
    private List<String> aliases = new ArrayList<>();
    private List<String> semanticTerms = new ArrayList<>();
    private Double minScore;

    public String getStreamer() {
        return streamer;
    }

    public void setStreamer(String streamer) {
        this.streamer = streamer;
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
