package com.streamsense.sentimentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();
    private Ml ml = new Ml();
    private History history = new History();
    private Processing processing = new Processing();

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

    public Processing getProcessing() {
        return processing;
    }

    public void setProcessing(Processing processing) {
        this.processing = processing;
    }

    public static class Topics {
        private String chatMessages;
        private String sentimentEvents;
        private String chatMessagesDlt;

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

        public String getChatMessagesDlt() {
            return chatMessagesDlt;
        }

        public void setChatMessagesDlt(String chatMessagesDlt) {
            this.chatMessagesDlt = chatMessagesDlt;
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
}
