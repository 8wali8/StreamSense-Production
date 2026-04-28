package com.streamsense.chatservice.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();
    private Twitch twitch = new Twitch();

    public Topics getTopics() {
        return topics;
    }

    public void setTopics(Topics topics) {
        this.topics = topics;
    }

    public Twitch getTwitch() {
        return twitch;
    }

    public void setTwitch(Twitch twitch) {
        this.twitch = twitch;
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

    public static class Twitch {
        private Chat chat = new Chat();

        public Chat getChat() {
            return chat;
        }

        public void setChat(Chat chat) {
            this.chat = chat;
        }
    }

    public static class Chat {
        private boolean enabled = false;
        private String host = "irc.chat.twitch.tv";
        private int port = 6697;
        private boolean ssl = true;
        private String username = "";
        private String oauthToken = "";
        private List<String> channels = new ArrayList<>();
        private long reconnectDelayMs = 5000;
        private long maxReconnectDelayMs = 60000;
        private int connectionTimeoutMs = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getOauthToken() {
            return oauthToken;
        }

        public void setOauthToken(String oauthToken) {
            this.oauthToken = oauthToken;
        }

        public List<String> getChannels() {
            return channels;
        }

        public void setChannels(List<String> channels) {
            this.channels = channels;
        }

        public long getReconnectDelayMs() {
            return reconnectDelayMs;
        }

        public void setReconnectDelayMs(long reconnectDelayMs) {
            this.reconnectDelayMs = reconnectDelayMs;
        }

        public long getMaxReconnectDelayMs() {
            return maxReconnectDelayMs;
        }

        public void setMaxReconnectDelayMs(long maxReconnectDelayMs) {
            this.maxReconnectDelayMs = maxReconnectDelayMs;
        }

        public int getConnectionTimeoutMs() {
            return connectionTimeoutMs;
        }

        public void setConnectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
        }
    }
}
