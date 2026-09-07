package com.streamsense.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.ratelimit.InMemoryRateLimiter;
import com.streamsense.apigateway.ratelimit.RateLimiter;
import com.streamsense.apigateway.ratelimit.RedisRateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class RateLimitStoreConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class))
            // Spring Boot's default, stated here because the test depends on it: a second bean with an existing
            // name is an error, not an override.
            .withAllowBeanDefinitionOverriding(false)
            .withUserConfiguration(TestSupport.class, RateLimitStoreConfig.class);

    @Test
    void defaultsToThePerInstanceStore() {
        runner.run(context -> assertThat(context).getBean(RateLimiter.class).isInstanceOf(InMemoryRateLimiter.class));
    }

    @Test
    void usesRedisWhenConfigured() {
        runner.withPropertyValues("streamsense.gateway.rate-limit-store=redis", "spring.data.redis.host=redis.invalid")
                .run(context -> {
                    assertThat(context).getBean(RateLimiter.class).isInstanceOf(RedisRateLimiter.class);
                    // The gateway's own (unused) limiter bean must be able to coexist with ours.
                    assertThat(context).hasBean("redisRateLimiter").hasBean("redisRateLimitStore");
                });
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

        /**
         * Stands in for the {@code redisRateLimiter} bean that Spring Cloud Gateway's (package-private)
         * {@code GatewayRedisAutoConfiguration} registers whenever reactive Redis is on the classpath. Our store
         * beans must not reuse that name, or the gateway refuses to start.
         */
        @Bean("redisRateLimiter")
        Object gatewayRedisRateLimiterStandIn() {
            return new Object();
        }
    }
}
