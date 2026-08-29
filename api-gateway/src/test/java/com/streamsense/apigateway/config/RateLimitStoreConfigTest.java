package com.streamsense.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.streamsense.apigateway.ratelimit.InMemoryRateLimiter;
import com.streamsense.apigateway.ratelimit.RateLimiter;
import com.streamsense.apigateway.ratelimit.RedisRateLimiter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RateLimitStoreConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class))
            .withUserConfiguration(TestSupport.class, RateLimitStoreConfig.class);

    @Test
    void defaultsToThePerInstanceStore() {
        runner.run(context -> assertThat(context).getBean(RateLimiter.class).isInstanceOf(InMemoryRateLimiter.class));
    }

    @Test
    void usesRedisWhenConfigured() {
        runner.withPropertyValues("streamsense.gateway.rate-limit-store=redis", "spring.data.redis.host=redis.invalid")
                .run(context -> assertThat(context).getBean(RateLimiter.class).isInstanceOf(RedisRateLimiter.class));
    }

    @Test
    void neverRegistersBothStores() {
        runner.withPropertyValues("streamsense.gateway.rate-limit-store=memory")
                .run(context -> assertThat(context).getBeans(RateLimiter.class).hasSize(1));
    }

    @Configuration
    @EnableConfigurationProperties(GatewayEdgeProperties.class)
    static class TestSupport {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
