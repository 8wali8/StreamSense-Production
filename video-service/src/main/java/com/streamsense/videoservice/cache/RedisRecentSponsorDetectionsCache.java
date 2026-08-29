package com.streamsense.videoservice.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.metrics.VideoMetrics;
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

@Component
public class RedisRecentSponsorDetectionsCache implements RecentSponsorDetectionsCache {

    private static final Logger log = LoggerFactory.getLogger(RedisRecentSponsorDetectionsCache.class);
    private static final String CACHE_NAME = "recentSponsorDetections";
    private static final TypeReference<List<SponsorDetectionEvent>> RECENT_DETECTIONS_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final StreamSenseProperties properties;
    private final VideoMetrics videoMetrics;

    public RedisRecentSponsorDetectionsCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            StreamSenseProperties properties,
            VideoMetrics videoMetrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.videoMetrics = videoMetrics;
    }

    @Override
    public Optional<List<SponsorDetectionEvent>> find(String streamer, int limit) {
        String cacheKey = cacheKey(streamer, limit);
        return videoMetrics.recordHistoryLookup(CACHE_NAME, "cache", () -> {
            try {
                String payload = redisTemplate.opsForValue().get(cacheKey);
                if (payload == null || payload.isBlank()) {
                    videoMetrics.incrementCacheMiss(CACHE_NAME);
                    return Optional.empty();
                }

                List<SponsorDetectionEvent> cached = objectMapper.readValue(payload, RECENT_DETECTIONS_TYPE);
                videoMetrics.incrementCacheHit(CACHE_NAME);
                log.info("sponsor history cache hit streamer={} limit={} key={}", streamer, limit, cacheKey);
                return Optional.of(cached);
            } catch (Exception e) {
                videoMetrics.incrementCacheMiss(CACHE_NAME);
                log.warn(
                        "sponsor history cache read failed streamer={} limit={} key={} error={}",
                        streamer,
                        limit,
                        cacheKey,
                        e.getMessage());
                delete(cacheKey);
                return Optional.empty();
            }
        });
    }

    @Override
    public void put(String streamer, int limit, List<SponsorDetectionEvent> recent) {
        String cacheKey = cacheKey(streamer, limit);
        try {
            redisTemplate
                    .opsForValue()
                    .set(
                            cacheKey,
                            objectMapper.writeValueAsString(recent),
                            Duration.ofSeconds(properties.getCache().getRecentTtlSeconds()));
        } catch (Exception e) {
            log.warn("sponsor history cache write failed key={} error={}", cacheKey, e.getMessage());
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
            log.warn("sponsor history cache eviction failed streamer={} error={}", streamer, e.getMessage());
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
