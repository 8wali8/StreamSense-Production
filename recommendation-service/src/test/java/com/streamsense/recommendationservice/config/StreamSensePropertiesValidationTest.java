package com.streamsense.recommendationservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StreamSensePropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsBaseUrlsAndDefaultTimeouts() {
        runner.withPropertyValues(
                        "streamsense.services.sentiment-service.base-url=http://sentiment-service:8083",
                        "streamsense.services.video-service.base-url=http://video-service:8084")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    StreamSenseProperties.Services services =
                            context.getBean(StreamSenseProperties.class).getServices();
                    assertThat(services.getSentimentService().getBaseUrl()).isEqualTo("http://sentiment-service:8083");
                    assertThat(services.getVideoService().getBaseUrl()).isEqualTo("http://video-service:8084");
                    assertThat(services.getConnectTimeoutMs()).isEqualTo(2000);
                    assertThat(services.getReadTimeoutMs()).isEqualTo(5000);
                });
    }

    @Test
    void missingBaseUrlFailsStartupInsteadOfDefaultingToLocalhost() {
        runner.withPropertyValues("streamsense.services.sentiment-service.base-url=http://sentiment-service:8083")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("videoService.baseUrl");
                });
    }

    @Configuration
    @EnableConfigurationProperties(StreamSenseProperties.class)
    static class PropertiesConfiguration {}
}
