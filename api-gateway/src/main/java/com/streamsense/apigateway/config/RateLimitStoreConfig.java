package com.streamsense.apigateway.config;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import com.streamsense.apigateway.ratelimit.InMemoryRateLimiter;
import com.streamsense.apigateway.ratelimit.RateLimiter;
import com.streamsense.apigateway.ratelimit.RedisRateLimiter;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Picks the rate-limit counter store from {@code streamsense.gateway.rate-limit-store}: {@code redis} (shared by
 * every replica; what Compose and Kubernetes run) or {@code memory} (per instance; local runs and tests).
 *
 * <p>The bean names deliberately avoid {@code redisRateLimiter}: Spring Cloud Gateway's
 * {@code GatewayRedisAutoConfiguration} registers a bean of that name as soon as reactive Redis is on the classpath,
 * and bean overriding is disabled, so a clash stops the gateway from starting.
 */
@Configuration
public class RateLimitStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitStoreConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.gateway", name = "rate-limit-store", havingValue = "redis")
    RateLimiter redisRateLimitStore(ReactiveStringRedisTemplate redisTemplate, GatewayEdgeProperties properties,
            MeterRegistry meterRegistry) {
        log.info("rate limit store: redis (fail-open={})", properties.isRateLimitFailOpen());
        return new RedisRateLimiter(redisTemplate, Clock.systemUTC(), properties.isRateLimitFailOpen(), meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.gateway", name = "rate-limit-store", havingValue = "memory", matchIfMissing = true)
    RateLimiter inMemoryRateLimitStore() {
        log.warn("rate limit store: memory; limits are counted per gateway instance, not across replicas");
        return new InMemoryRateLimiter();
    }
}
