package com.streamsense.sentimentservice.dto;

import java.util.ArrayList;
import java.util.List;

public class MlRelevanceResponse {

    private boolean sponsorRelevant;
    private String matchedSponsor;
    private List<String> matchedTerms = new ArrayList<>();
    private double relevanceScore;
    private String relevanceReason;
    private String modelVersion;

    public static MlRelevanceResponse notRelevant(String reason, String modelVersion) {
        MlRelevanceResponse response = new MlRelevanceResponse();
        response.setSponsorRelevant(false);
        response.setMatchedTerms(List.of());
        response.setRelevanceScore(0.0d);
        response.setRelevanceReason(reason);
        response.setModelVersion(modelVersion);
        return response;
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

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }
}
