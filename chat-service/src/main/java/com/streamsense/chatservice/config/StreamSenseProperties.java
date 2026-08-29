package com.streamsense.chatservice.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();
    private Twitch twitch = new Twitch();
    private Replay replay = new Replay();

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

    public Replay getReplay() {
        return replay;
    }

    public void setReplay(Replay replay) {
        this.replay = replay;
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

    public static class Replay {
        private Map<String, ReplayAlias> aliases = new LinkedHashMap<>();
        private TwitchGraphql twitchGraphql = new TwitchGraphql();

        public Map<String, ReplayAlias> getAliases() {
            return aliases;
        }

        public void setAliases(Map<String, ReplayAlias> aliases) {
            this.aliases = aliases;
        }

        public TwitchGraphql getTwitchGraphql() {
            return twitchGraphql;
        }

        public void setTwitchGraphql(TwitchGraphql twitchGraphql) {
            this.twitchGraphql = twitchGraphql;
        }
    }

    public static class ReplayAlias {
        private String provider = "twitch";
        private String vodId = "";
        private String vodUrl = "";
        private double replaySpeed = 1.0;
        private double startOffsetSeconds = 0.0;
        private String source = "TWITCH_VOD_REPLAY";
        private boolean loop = true;
        private String chatFixturePath = "";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getVodId() {
            return vodId;
        }

        public void setVodId(String vodId) {
            this.vodId = vodId;
        }

        public String getVodUrl() {
            return vodUrl;
        }

        public void setVodUrl(String vodUrl) {
            this.vodUrl = vodUrl;
        }

        public double getReplaySpeed() {
            return replaySpeed;
        }

        public void setReplaySpeed(double replaySpeed) {
            this.replaySpeed = replaySpeed;
        }

        public double getStartOffsetSeconds() {
            return startOffsetSeconds;
        }

        public void setStartOffsetSeconds(double startOffsetSeconds) {
            this.startOffsetSeconds = startOffsetSeconds;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public boolean isLoop() {
            return loop;
        }

        public void setLoop(boolean loop) {
            this.loop = loop;
        }

        public String getChatFixturePath() {
            return chatFixturePath;
        }

        public void setChatFixturePath(String chatFixturePath) {
            this.chatFixturePath = chatFixturePath;
        }
    }

    public static class TwitchGraphql {
        private String endpoint = "https://gql.twitch.tv/gql";
        private String clientId = "kimne78kx3ncx6brgo4mv6wki5h1ko";
        private int requestTimeoutMs = 10000;
        private int maxPages = 200;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public int getRequestTimeoutMs() {
            return requestTimeoutMs;
        }

        public void setRequestTimeoutMs(int requestTimeoutMs) {
            this.requestTimeoutMs = requestTimeoutMs;
        }

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }
    }
}
