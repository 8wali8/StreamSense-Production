package com.streamsense.recommendationservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    @Valid
    private final Services services = new Services();

    private final Recommendations recommendations = new Recommendations();

    public Services getServices() {
        return services;
    }

    public Recommendations getRecommendations() {
        return recommendations;
    }

    public static class Services {

        @Valid
        private final DownstreamService sentimentService = new DownstreamService();

        @Valid
        private final DownstreamService videoService = new DownstreamService();
        /** Time allowed to open a connection to sentiment-service or video-service. */
        @Min(1)
        private int connectTimeoutMs = 2000;
        /** Time allowed to wait for a response once the request is sent. */
        @Min(1)
        private int readTimeoutMs = 5000;

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public DownstreamService getSentimentService() {
            return sentimentService;
        }

        public DownstreamService getVideoService() {
            return videoService;
        }
    }

    public static class DownstreamService {

        /** Required: there is no localhost fallback, a missing URL stops the service from starting. */
        @NotBlank
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Recommendations {

        private int defaultLimit = 3;
        private int maxLimit = 6;
        private int signalWindowLimit = 20;
        private String refreshMode = "restart";
        private String experimentName = "recommendation-ranking-v1";
        private String activeVariant = "balanced";
        private final Map<String, Variant> variants = new LinkedHashMap<>();

        public int getDefaultLimit() {
            return defaultLimit;
        }

        public void setDefaultLimit(int defaultLimit) {
            this.defaultLimit = defaultLimit;
        }

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }

        public int getSignalWindowLimit() {
            return signalWindowLimit;
        }

        public void setSignalWindowLimit(int signalWindowLimit) {
            this.signalWindowLimit = signalWindowLimit;
        }

        public String getRefreshMode() {
            return refreshMode;
        }

        public void setRefreshMode(String refreshMode) {
            this.refreshMode = refreshMode;
        }

        public String getExperimentName() {
            return experimentName;
        }

        public void setExperimentName(String experimentName) {
            this.experimentName = experimentName;
        }

        public String getActiveVariant() {
            return activeVariant;
        }

        public void setActiveVariant(String activeVariant) {
            this.activeVariant = activeVariant;
        }

        public Map<String, Variant> getVariants() {
            return variants;
        }

        public String resolveActiveVariantId() {
            Variant directMatch = variants.get(activeVariant);
            if (directMatch != null && directMatch.isEnabled()) {
                return activeVariant;
            }

            return variants.entrySet().stream()
                    .filter(entry -> entry.getValue().isEnabled())
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .orElse(activeVariant);
        }

        public Variant resolveActiveVariant() {
            Variant directMatch = variants.get(activeVariant);
            if (directMatch != null && directMatch.isEnabled()) {
                return directMatch;
            }

            return variants.entrySet().stream()
                    .filter(entry -> entry.getValue().isEnabled())
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElseGet(Variant::new);
        }
    }

    public static class Variant {

        private boolean enabled = true;
        private double positiveWeight = 1.0d;
        private double sponsorWeight = 0.9d;
        private double cautionWeight = 1.1d;
        private double momentumThreshold = 0.15d;
        private double sponsorConfidenceThreshold = 0.65d;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getPositiveWeight() {
            return positiveWeight;
        }

        public void setPositiveWeight(double positiveWeight) {
            this.positiveWeight = positiveWeight;
        }

        public double getSponsorWeight() {
            return sponsorWeight;
        }

        public void setSponsorWeight(double sponsorWeight) {
            this.sponsorWeight = sponsorWeight;
        }

        public double getCautionWeight() {
            return cautionWeight;
        }

        public void setCautionWeight(double cautionWeight) {
            this.cautionWeight = cautionWeight;
        }

        public double getMomentumThreshold() {
            return momentumThreshold;
        }

        public void setMomentumThreshold(double momentumThreshold) {
            this.momentumThreshold = momentumThreshold;
        }

        public double getSponsorConfidenceThreshold() {
            return sponsorConfidenceThreshold;
        }

        public void setSponsorConfidenceThreshold(double sponsorConfidenceThreshold) {
            this.sponsorConfidenceThreshold = sponsorConfidenceThreshold;
        }
    }
}
