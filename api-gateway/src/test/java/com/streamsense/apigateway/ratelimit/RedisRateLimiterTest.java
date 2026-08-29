package com.streamsense.apigateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.ratelimit.RateLimiter.RateLimitDecision;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Runs against a real Redis (the same pinned image Compose uses); skipped where Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse(
                    "redis:7.4.11-alpine@sha256:ff02b58f971e7d7d156a1267e283fcbbeee91773b6aa36c49dac28ecfe28eadf"))
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static ReactiveStringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        connectionFactory = factoryFor(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis = new ReactiveStringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void allowsRequestsWithinTheWindowAndRejectsOverflow() {
        RedisRateLimiter limiter = limiter(redis, "2026-04-11T12:00:00Z", true);
        String bucket = "chat-ingest:203.0.113.10:" + System.nanoTime();

        assertThat(limiter.acquire(bucket, 2, 60).block()).isEqualTo(new RateLimitDecision(true, 1, 1775908860L));
        assertThat(limiter.acquire(bucket, 2, 60).block()).isEqualTo(new RateLimitDecision(true, 0, 1775908860L));
        assertThat(limiter.acquire(bucket, 2, 60).block()).isEqualTo(new RateLimitDecision(false, 0, 1775908860L));
    }

    @Test
    void countsSeparateClientsAndWindowsSeparately() {
        RedisRateLimiter limiter = limiter(redis, "2026-04-11T12:00:00Z", true);
        String bucketA = "chat-ingest:198.51.100.1:" + System.nanoTime();
        String bucketB = "chat-ingest:198.51.100.2:" + System.nanoTime();

        assertThat(limiter.acquire(bucketA, 1, 60).block().allowed()).isTrue();
        assertThat(limiter.acquire(bucketB, 1, 60).block().allowed()).isTrue();
        assertThat(limiter.acquire(bucketA, 1, 60).block().allowed()).isFalse();

        RedisRateLimiter nextWindow = limiter(redis, "2026-04-11T12:01:00Z", true);
        assertThat(nextWindow.acquire(bucketA, 1, 60).block().allowed()).isTrue();
    }

    @Test
    void keysExpireOneSecondAfterTheirWindow() {
        RedisRateLimiter limiter = limiter(redis, "2026-04-11T12:00:00Z", true);
        String bucket = "video-upload:203.0.113.20:" + System.nanoTime();

        limiter.acquire(bucket, 5, 60).block();

        Long ttl = redis.getExpire(RedisRateLimiter.KEY_PREFIX + bucket + ":1775908800")
                .block()
                .toSeconds();
        assertThat(ttl).isBetween(55L, 61L);
    }

    @Test
    void failsOpenOrClosedWhenRedisIsUnreachable() {
        LettuceConnectionFactory unreachable = factoryFor("127.0.0.1", 1);
        ReactiveStringRedisTemplate brokenRedis = new ReactiveStringRedisTemplate(unreachable);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        try {
            RedisRateLimiter open =
                    new RedisRateLimiter(brokenRedis, fixedClock("2026-04-11T12:00:00Z"), true, registry);
            RedisRateLimiter closed =
                    new RedisRateLimiter(brokenRedis, fixedClock("2026-04-11T12:00:00Z"), false, registry);

            assertThat(open.acquire("chat-ingest:x", 3, 60).block(Duration.ofSeconds(10)))
                    .isEqualTo(new RateLimitDecision(true, 3, 1775908860L));
            assertThat(closed.acquire("chat-ingest:x", 3, 60).block(Duration.ofSeconds(10)))
                    .isEqualTo(new RateLimitDecision(false, 0, 1775908860L));
            assertThat(registry.get("streamsense_gateway_rate_limit_store_errors_total")
                            .counter()
                            .count())
                    .isEqualTo(2.0d);
        } finally {
            unreachable.destroy();
        }
    }

    private static RedisRateLimiter limiter(ReactiveStringRedisTemplate template, String now, boolean failOpen) {
        return new RedisRateLimiter(template, fixedClock(now), failOpen, new SimpleMeterRegistry());
    }

    private static Clock fixedClock(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    private static LettuceConnectionFactory factoryFor(String host, int port) {
        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .build();
        LettuceConnectionFactory factory =
                new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port), clientConfiguration);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }
}
