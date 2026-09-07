package com.streamsense.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DownstreamServicesPropertiesTest {

    private static final String[] ALL_BASE_URLS = {
            "streamsense.services.chat-service.base-url=http://chat-service:8081",
            "streamsense.services.recommendation-service.base-url=http://recommendation-service:8082",
            "streamsense.services.sentiment-service.base-url=http://sentiment-service:8083",
            "streamsense.services.video-service.base-url=http://video-service:8084",
            "streamsense.services.video-capture-service.base-url=http://video-capture-service:8090",
            "streamsense.services.analytics-service.base-url=http://analytics-service:8085"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsEveryBaseUrlAndDefaultTimeouts() {
        runner.withPropertyValues(ALL_BASE_URLS).run(context -> {
            assertThat(context).hasNotFailed();
            DownstreamServicesProperties properties = context.getBean(DownstreamServicesProperties.class);
            assertThat(properties.getRecommendationService().getBaseUrl()).isEqualTo("http://recommendation-service:8082");
            assertThat(properties.getAnalyticsService().getBaseUrl()).isEqualTo("http://analytics-service:8085");
            assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void missingBaseUrlFailsStartupInsteadOfDefaultingToLocalhost() {
        runner.withPropertyValues(
                "streamsense.services.chat-service.base-url=http://chat-service:8081",
                "streamsense.services.sentiment-service.base-url=http://sentiment-service:8083",
                "streamsense.services.video-service.base-url=http://video-service:8084",
                "streamsense.services.video-capture-service.base-url=http://video-capture-service:8090",
                "streamsense.services.analytics-service.base-url=http://analytics-service:8085"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("recommendationService.baseUrl");
        });
    }

    @Test
    void timeoutsAreConfigurable() {
        runner.withPropertyValues(ALL_BASE_URLS)
                .withPropertyValues(
                        "streamsense.services.connect-timeout=500ms",
                        "streamsense.services.response-timeout=1500ms")
                .run(context -> {
                    DownstreamServicesProperties properties = context.getBean(DownstreamServicesProperties.class);
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(500));
                    assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofMillis(1500));
                });
    }

    @Configuration
    @EnableConfigurationProperties(DownstreamServicesProperties.class)
    static class PropertiesConfiguration {
    }
}
