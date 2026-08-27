package com.streamsense.analyticsservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private final Topics topics = new Topics();
    private final Analytics analytics = new Analytics();

    public Topics getTopics() {
        return topics;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public static class Topics {
        private String chatMessages = "stream.chat.messages";
        private String sentimentEvents = "stream.sentiment.events";
        private String sponsorDetections = "stream.sponsor.detections";
        private String transcriptSentimentEvents = "stream.transcript.sentiment.events";

        public String getChatMessages() {
            return chatMessages;
        }

        public void setChatMessages(String chatMessages) {
            this.chatMessages = chatMessages;
        }

        public String getSentimentEvents() {
            return sentimentEvents;
        }

        public void setSentimentEvents(String sentimentEvents) {
            this.sentimentEvents = sentimentEvents;
        }

        public String getSponsorDetections() {
            return sponsorDetections;
        }

        public void setSponsorDetections(String sponsorDetections) {
            this.sponsorDetections = sponsorDetections;
        }

        public String getTranscriptSentimentEvents() {
            return transcriptSentimentEvents;
        }

        public void setTranscriptSentimentEvents(String transcriptSentimentEvents) {
            this.transcriptSentimentEvents = transcriptSentimentEvents;
        }
    }

    public static class Analytics {
        private int bucketSizeSeconds = 60;
        private int defaultWindowMinutes = 15;
        private int maxWindowMinutes = 1440;
        private long estimatedSponsorExposureMsPerDetection = 10000;
        private double minimumSponsorConfidence = 0.50;
        private double negativeSpikeRatioThreshold = 0.60;
        private long negativeSpikeMinimumEvents = 10;
        private long engagementSpikeMinimumMessages = 20;
        private double engagementSpikeMultiplier = 2.0;
        private int engagementSpikeTrailingWindowMinutes = 5;
        private long lowDataMinimumEvents = 5;

        public int getBucketSizeSeconds() {
            return bucketSizeSeconds;
        }

        public void setBucketSizeSeconds(int bucketSizeSeconds) {
            this.bucketSizeSeconds = bucketSizeSeconds;
        }

        public int getDefaultWindowMinutes() {
            return defaultWindowMinutes;
        }

        public void setDefaultWindowMinutes(int defaultWindowMinutes) {
            this.defaultWindowMinutes = defaultWindowMinutes;
        }

        public int getMaxWindowMinutes() {
            return maxWindowMinutes;
        }

        public void setMaxWindowMinutes(int maxWindowMinutes) {
            this.maxWindowMinutes = maxWindowMinutes;
        }

        public long getEstimatedSponsorExposureMsPerDetection() {
            return estimatedSponsorExposureMsPerDetection;
        }

        public void setEstimatedSponsorExposureMsPerDetection(long estimatedSponsorExposureMsPerDetection) {
            this.estimatedSponsorExposureMsPerDetection = estimatedSponsorExposureMsPerDetection;
        }

        public double getMinimumSponsorConfidence() {
            return minimumSponsorConfidence;
        }

        public void setMinimumSponsorConfidence(double minimumSponsorConfidence) {
            this.minimumSponsorConfidence = minimumSponsorConfidence;
        }

        public double getNegativeSpikeRatioThreshold() {
            return negativeSpikeRatioThreshold;
        }

        public void setNegativeSpikeRatioThreshold(double negativeSpikeRatioThreshold) {
            this.negativeSpikeRatioThreshold = negativeSpikeRatioThreshold;
        }

        public long getNegativeSpikeMinimumEvents() {
            return negativeSpikeMinimumEvents;
        }

        public void setNegativeSpikeMinimumEvents(long negativeSpikeMinimumEvents) {
            this.negativeSpikeMinimumEvents = negativeSpikeMinimumEvents;
        }

        public long getEngagementSpikeMinimumMessages() {
            return engagementSpikeMinimumMessages;
        }

        public void setEngagementSpikeMinimumMessages(long engagementSpikeMinimumMessages) {
            this.engagementSpikeMinimumMessages = engagementSpikeMinimumMessages;
        }

        public double getEngagementSpikeMultiplier() {
            return engagementSpikeMultiplier;
        }

        public void setEngagementSpikeMultiplier(double engagementSpikeMultiplier) {
            this.engagementSpikeMultiplier = engagementSpikeMultiplier;
        }

        public int getEngagementSpikeTrailingWindowMinutes() {
            return engagementSpikeTrailingWindowMinutes;
        }

        public void setEngagementSpikeTrailingWindowMinutes(int engagementSpikeTrailingWindowMinutes) {
            this.engagementSpikeTrailingWindowMinutes = engagementSpikeTrailingWindowMinutes;
        }

        public long getLowDataMinimumEvents() {
            return lowDataMinimumEvents;
        }

        public void setLowDataMinimumEvents(long lowDataMinimumEvents) {
            this.lowDataMinimumEvents = lowDataMinimumEvents;
        }
    }
}
