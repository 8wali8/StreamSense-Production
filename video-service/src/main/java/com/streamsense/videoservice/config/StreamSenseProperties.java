package com.streamsense.videoservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();
    private Ml ml = new Ml();
    private History history = new History();
    private Payload payload = new Payload();

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public Ml getMl() {
        return ml;
    }

    public void setMl(Ml ml) {
        this.ml = ml;
    }

    public History getHistory() {
        return history;
    }

    public void setHistory(History history) {
        this.history = history;
    }

    public Payload getPayload() {
        return payload;
    }

    public void setPayload(Payload payload) {
        this.payload = payload;
    }

    public static class Topics {
        private String videoFrames;
        private String sponsorDetections;

        public String getVideoFrames() {
            return videoFrames;
        }

        public void setVideoFrames(String videoFrames) {
            this.videoFrames = videoFrames;
        }

        public String getSponsorDetections() {
            return sponsorDetections;
        }

        public void setSponsorDetections(String sponsorDetections) {
            this.sponsorDetections = sponsorDetections;
        }
    }

    public static class Ml {
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

    public static class History {
        private int defaultLimit = 20;
        private int maxLimit = 100;

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
    }

    public static class Payload {
        private int maxFrameRefLength = 512;

        public int getMaxFrameRefLength() {
            return maxFrameRefLength;
        }

        public void setMaxFrameRefLength(int maxFrameRefLength) {
            this.maxFrameRefLength = maxFrameRefLength;
        }
    }
}
