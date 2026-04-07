package com.streamsense.chatservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public static class Topics {
        private String chatMessages;

        public String getChatMessages() {
            return chatMessages;
        }

        public void setChatMessages(String chatMessages) {
            this.chatMessages = chatMessages;
        }
    }
}
