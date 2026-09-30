package com.streamsense.sentimentservice.dto;

import java.util.ArrayList;
import java.util.List;

public class SponsorRelevanceProfile {

    private String streamer;
    private String sponsor;
    private List<String> aliases = new ArrayList<>();
    private List<String> semanticTerms = new ArrayList<>();
    /** The score in effect: the channel's override, else the catalog entry's, else the configured default. */
    private Double minScore;
    /** The score the channel chose for itself, or null when it follows the catalog or the default. */
    private Double minScoreOverride;

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

    public Double getMinScoreOverride() {
        return minScoreOverride;
    }

    public void setMinScoreOverride(Double minScoreOverride) {
        this.minScoreOverride = minScoreOverride;
    }
}
