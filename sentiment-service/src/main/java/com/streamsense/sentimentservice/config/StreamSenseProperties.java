package com.streamsense.sentimentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();
    private Ml ml = new Ml();
    private History history = new History();

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

    public static class Topics {
        private String chatMessages;
        private String sentimentEvents;

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
    }

    public static class Ml {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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
}
