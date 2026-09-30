package com.streamsense.videoservice.dto;

public class MlSponsorResponse {

    private String sponsor;
    private double confidence;
    private String modelVersion;
    private double x;
    private double y;
    private double width;
    private double height;
    /** DETECTED or NOT_DETECTED; null from an engine built before outcomes, which only ever detected. */
    private String outcome;

    public String getSponsor() {
        return sponsor;
    }

    public void setSponsor(String sponsor) {
        this.sponsor = sponsor;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    /** A response with no box: the fallback ({@code UNKNOWN}, model version {@code fallback}) and the no-logo answer are both this shape. */
    public static MlSponsorResponse empty(String sponsor, String modelVersion, String outcome) {
        MlSponsorResponse response = new MlSponsorResponse();
        response.setSponsor(sponsor);
        response.setConfidence(0.0d);
        response.setModelVersion(modelVersion);
        response.setX(0.0d);
        response.setY(0.0d);
        response.setWidth(0.0d);
        response.setHeight(0.0d);
        response.setOutcome(outcome);
        return response;
    }
}
