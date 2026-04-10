package com.streamsense.sentimentservice.cache;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;

@Component
public class RedisRecentSentimentCache implements RecentSentimentCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRecentSentimentCache.class);
    private static final String CACHE_NAME = "recentSentiment";
    private static final TypeReference<List<SentimentAnalysisEvent>> RECENT_SENTIMENT_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StreamSenseProperties properties;
    private final SentimentMetrics sentimentMetrics;

    public RedisRecentSentimentCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StreamSenseProperties properties,
            SentimentMetrics sentimentMetrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.sentimentMetrics = sentimentMetrics;
    }

    @Override
    public Optional<List<SentimentAnalysisEvent>> find(String streamer, int limit) {
        String cacheKey = cacheKey(streamer, limit);
        return sentimentMetrics.recordHistoryLookup(CACHE_NAME, "cache", () -> {
            try {
                String payload = redisTemplate.opsForValue().get(cacheKey);
                if (payload == null || payload.isBlank()) {
                    sentimentMetrics.incrementCacheMiss(CACHE_NAME);
                    return Optional.empty();
                }

                List<SentimentAnalysisEvent> cached = objectMapper.readValue(payload, RECENT_SENTIMENT_TYPE);
                sentimentMetrics.incrementCacheHit(CACHE_NAME);
                log.info("sentiment history cache hit streamer={} limit={} key={}", streamer, limit, cacheKey);
                return Optional.of(cached);
            } catch (Exception e) {
                sentimentMetrics.incrementCacheMiss(CACHE_NAME);
                log.warn("sentiment history cache read failed streamer={} limit={} key={} error={}",
                        streamer, limit, cacheKey, e.getMessage());
                delete(cacheKey);
                return Optional.empty();
            }
        });
    }

    @Override
    public void put(String streamer, int limit, List<SentimentAnalysisEvent> recent) {
        String cacheKey = cacheKey(streamer, limit);
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(recent),
                    Duration.ofSeconds(properties.getCache().getRecentTtlSeconds()));
        } catch (Exception e) {
            log.warn("sentiment history cache write failed key={} error={}", cacheKey, e.getMessage());
        }
    }

    @Override
    public void evict(String streamer) {
        String keyPattern = properties.getCache().getRecentPrefix() + ":" + encodeKeyPart(streamer) + ":*";
        try {
            Set<String> keys = redisTemplate.keys(keyPattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("sentiment history cache eviction failed streamer={} error={}", streamer, e.getMessage());
        }
    }

    private void delete(String cacheKey) {
        try {
            redisTemplate.delete(cacheKey);
        } catch (RuntimeException ignored) {
            // ignore cache cleanup failures and fall back to Postgres
        }
    }

    private String cacheKey(String streamer, int limit) {
        return properties.getCache().getRecentPrefix() + ":" + encodeKeyPart(streamer) + ":" + limit;
    }

    private String encodeKeyPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
