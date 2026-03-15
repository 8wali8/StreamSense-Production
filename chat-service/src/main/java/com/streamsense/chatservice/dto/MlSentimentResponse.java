package com.streamsense.chatservice.dto;

public class MlSentimentResponse {

    private String label;
    private double score;
    private String modelVersion;

    public MlSentimentResponse() {
    }

    public MlSentimentResponse(String label, double score, String modelVersion) {
        this.label = label;
        this.score = score;
        this.modelVersion = modelVersion;
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
}