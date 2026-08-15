package com.streamsense.sentimentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense")
public class StreamSenseProperties {

    private Topics topics = new Topics();
    private Ml ml = new Ml();
    private History history = new History();
    private Cache cache = new Cache();
    private Processing processing = new Processing();
    private Sentiment sentiment = new Sentiment();

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

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Processing getProcessing() {
        return processing;
    }

    public void setProcessing(Processing processing) {
        this.processing = processing;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    public static class Topics {
        private String chatMessages = "stream.chat.messages";
        private String sentimentEvents = "stream.sentiment.events";
        private String chatMessagesDlt = "stream.chat.messages.dlt";
        private String transcriptSegments = "stream.transcript.segments";
        private String transcriptSentimentEvents = "stream.transcript.sentiment.events";
        private String transcriptSegmentsDlt = "stream.transcript.segments.dlt";

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

        public String getTranscriptSegments() {
            return transcriptSegments;
        }

        public void setTranscriptSegments(String transcriptSegments) {
            this.transcriptSegments = transcriptSegments;
        }

        public String getTranscriptSentimentEvents() {
            return transcriptSentimentEvents;
        }

        public void setTranscriptSentimentEvents(String transcriptSentimentEvents) {
            this.transcriptSentimentEvents = transcriptSentimentEvents;
        }

        public String getTranscriptSegmentsDlt() {
            return transcriptSegmentsDlt;
        }

        public void setTranscriptSegmentsDlt(String transcriptSegmentsDlt) {
            this.transcriptSegmentsDlt = transcriptSegmentsDlt;
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

    public static class Cache {
        private String recentPrefix = "sentiment:recent";
        private long recentTtlSeconds = 60;

        public String getRecentPrefix() {
            return recentPrefix;
        }

        public void setRecentPrefix(String recentPrefix) {
            this.recentPrefix = recentPrefix;
        }

        public long getRecentTtlSeconds() {
            return recentTtlSeconds;
        }

        public void setRecentTtlSeconds(long recentTtlSeconds) {
            this.recentTtlSeconds = recentTtlSeconds;
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

    public static class Sentiment {
        private Relevance relevance = new Relevance();

        public Relevance getRelevance() {
            return relevance;
        }

        public void setRelevance(Relevance relevance) {
            this.relevance = relevance;
        }
    }

    public static class Relevance {
        private boolean enabled = true;
        private double minScore = 0.50d;
        private java.util.List<Sponsor> sponsors = new java.util.ArrayList<>();
        private java.util.List<Seed> seeds = new java.util.ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public java.util.List<Sponsor> getSponsors() {
            return sponsors;
        }

        public void setSponsors(java.util.List<Sponsor> sponsors) {
            this.sponsors = sponsors != null ? sponsors : new java.util.ArrayList<>();
        }

        public java.util.List<Seed> getSeeds() {
            return seeds;
        }

        public void setSeeds(java.util.List<Seed> seeds) {
            this.seeds = seeds != null ? seeds : new java.util.ArrayList<>();
        }
    }

    public static class Seed {
        private String streamer;
        private String sponsor;
        private Double minScore;

        public String getStreamer() {
            return streamer;
        }

        public void setStreamer(String streamer) {
            this.streamer = streamer;
        }

        public String getSponsor() {
            return sponsor;
        }

        public void setSponsor(String sponsor) {
            this.sponsor = sponsor;
        }

        public Double getMinScore() {
            return minScore;
        }

        public void setMinScore(Double minScore) {
            this.minScore = minScore;
        }
    }

    public static class Sponsor {
        private String name;
        private java.util.List<String> aliases = new java.util.ArrayList<>();
        private java.util.List<String> semanticTerms = new java.util.ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public java.util.List<String> getAliases() {
            return aliases;
        }

        public void setAliases(java.util.List<String> aliases) {
            this.aliases = aliases != null ? aliases : new java.util.ArrayList<>();
        }

        public java.util.List<String> getSemanticTerms() {
            return semanticTerms;
        }

        public void setSemanticTerms(java.util.List<String> semanticTerms) {
            this.semanticTerms = semanticTerms != null ? semanticTerms : new java.util.ArrayList<>();
        }
    }
}
