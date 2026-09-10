package com.streamsense.analyticsservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private final Topics topics = new Topics();
    private final Analytics analytics = new Analytics();
    private final Processing processing = new Processing();
    private final Twitch twitch = new Twitch();
    private final Services services = new Services();

    public Topics getTopics() {
        return topics;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public Processing getProcessing() {
        return processing;
    }

    public Twitch getTwitch() {
        return twitch;
    }

    public Services getServices() {
        return services;
    }

    /** Other StreamSense services this one calls. No base URL means the call is never made. */
    public static class Services {
        private final Endpoint sentimentService = new Endpoint();
        private final Endpoint chatService = new Endpoint();
        private final Endpoint videoCaptureService = new Endpoint();

        /** sentiment-service, for pointing relevance at a deal's sponsor. */
        public Endpoint getSentimentService() {
            return sentimentService;
        }

        /** chat-service, for replaying a recording's chat into a VOD import. */
        public Endpoint getChatService() {
            return chatService;
        }

        /** video-capture-service, for replaying a recording's frames and audio into a VOD import. */
        public Endpoint getVideoCaptureService() {
            return videoCaptureService;
        }
    }

    /** A bounded HTTP endpoint of another StreamSense service. */
    public static class Endpoint {
        private String baseUrl;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 3000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

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
    }

    public static class Topics {
        private String chatMessages = "stream.chat.messages";
        private String sentimentEvents = "stream.sentiment.events";
        private String sponsorDetections = "stream.sponsor.detections";
        private String transcriptSentimentEvents = "stream.transcript.sentiment.events";
        private String chatMessagesDlt = "stream.chat.messages.analytics.dlt";
        private String sentimentEventsDlt = "stream.sentiment.events.analytics.dlt";
        private String sponsorDetectionsDlt = "stream.sponsor.detections.analytics.dlt";
        private String transcriptSentimentEventsDlt = "stream.transcript.sentiment.events.analytics.dlt";

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

        public String getChatMessagesDlt() {
            return chatMessagesDlt;
        }

        public void setChatMessagesDlt(String chatMessagesDlt) {
            this.chatMessagesDlt = chatMessagesDlt;
        }

        public String getSentimentEventsDlt() {
            return sentimentEventsDlt;
        }

        public void setSentimentEventsDlt(String sentimentEventsDlt) {
            this.sentimentEventsDlt = sentimentEventsDlt;
        }

        public String getSponsorDetectionsDlt() {
            return sponsorDetectionsDlt;
        }

        public void setSponsorDetectionsDlt(String sponsorDetectionsDlt) {
            this.sponsorDetectionsDlt = sponsorDetectionsDlt;
        }

        public String getTranscriptSentimentEventsDlt() {
            return transcriptSentimentEventsDlt;
        }

        public void setTranscriptSentimentEventsDlt(String transcriptSentimentEventsDlt) {
            this.transcriptSentimentEventsDlt = transcriptSentimentEventsDlt;
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
        /** A capture session with no event for this long is closed at its last event. */
        private int captureSessionIdleCloseMinutes = 10;
        /** Longest absolute range a query may ask for. */
        private int maxRangeHours = 48;

        private final Value value = new Value();
        private final Response response = new Response();

        public int getMaxRangeHours() {
            return maxRangeHours;
        }

        public void setMaxRangeHours(int maxRangeHours) {
            this.maxRangeHours = maxRangeHours;
        }

        public Value getValue() {
            return value;
        }

        public Response getResponse() {
            return response;
        }

        public int getCaptureSessionIdleCloseMinutes() {
            return captureSessionIdleCloseMinutes;
        }

        public void setCaptureSessionIdleCloseMinutes(int captureSessionIdleCloseMinutes) {
            this.captureSessionIdleCloseMinutes = captureSessionIdleCloseMinutes;
        }

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

    public static class Processing {
        private long retryBackoffMs = 1000;
        private long maxRetries = 2;

        public long getRetryBackoffMs() {
            return retryBackoffMs;
        }

        public void setRetryBackoffMs(long retryBackoffMs) {
            this.retryBackoffMs = retryBackoffMs;
        }

        public long getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(long maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public static class Twitch {
        private final Helix helix = new Helix();

        public Helix getHelix() {
            return helix;
        }
    }

    /** The Twitch Helix poller that turns live broadcasts into sessions with viewer counts. */
    public static class Helix {
        private boolean enabled = false;
        private String clientId;
        private String clientSecret;
        private String baseUrl = "https://api.twitch.tv/helix";
        private String tokenUrl = "https://id.twitch.tv/oauth2/token";
        private long pollIntervalMs = 60_000L;
        private long initialDelayMs = 15_000L;
        private java.util.List<String> channels = new java.util.ArrayList<>();
        private int watchStreamersSeenWithinHours = 24;
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;
        private long tokenRefreshMarginSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public java.util.List<String> getChannels() {
            return channels;
        }

        public void setChannels(java.util.List<String> channels) {
            this.channels = channels == null ? new java.util.ArrayList<>() : channels;
        }

        public int getWatchStreamersSeenWithinHours() {
            return watchStreamersSeenWithinHours;
        }

        public void setWatchStreamersSeenWithinHours(int watchStreamersSeenWithinHours) {
            this.watchStreamersSeenWithinHours = watchStreamersSeenWithinHours;
        }

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

        public long getTokenRefreshMarginSeconds() {
            return tokenRefreshMarginSeconds;
        }

        public void setTokenRefreshMarginSeconds(long tokenRefreshMarginSeconds) {
            this.tokenRefreshMarginSeconds = tokenRefreshMarginSeconds;
        }
    }

    /** Media value assumptions, overridable per request (and per deal once deals exist). */
    public static class Value {
        private double cpmPer30sEquivalent = 12.0d;
        private double hostReadRatePer1000 = 15.0d;
        private double prominenceBase = 0.5d;
        private double prominenceAreaScale = 10.0d;

        public double getCpmPer30sEquivalent() {
            return cpmPer30sEquivalent;
        }

        public void setCpmPer30sEquivalent(double cpmPer30sEquivalent) {
            this.cpmPer30sEquivalent = cpmPer30sEquivalent;
        }

        public double getHostReadRatePer1000() {
            return hostReadRatePer1000;
        }

        public void setHostReadRatePer1000(double hostReadRatePer1000) {
            this.hostReadRatePer1000 = hostReadRatePer1000;
        }

        public double getProminenceBase() {
            return prominenceBase;
        }

        public void setProminenceBase(double prominenceBase) {
            this.prominenceBase = prominenceBase;
        }

        public double getProminenceAreaScale() {
            return prominenceAreaScale;
        }

        public void setProminenceAreaScale(double prominenceAreaScale) {
            this.prominenceAreaScale = prominenceAreaScale;
        }
    }

    /** Which chat command and link host to count when a request does not name them. */
    public static class Response {
        private String defaultChatCommand;
        private String defaultTrackedLinkHost;

        public String getDefaultChatCommand() {
            return defaultChatCommand;
        }

        public void setDefaultChatCommand(String defaultChatCommand) {
            this.defaultChatCommand = defaultChatCommand;
        }

        public String getDefaultTrackedLinkHost() {
            return defaultTrackedLinkHost;
        }

        public void setDefaultTrackedLinkHost(String defaultTrackedLinkHost) {
            this.defaultTrackedLinkHost = defaultTrackedLinkHost;
        }
    }
}
