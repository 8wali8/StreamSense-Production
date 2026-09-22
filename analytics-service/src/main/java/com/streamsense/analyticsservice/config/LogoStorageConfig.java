package com.streamsense.analyticsservice.config;

import com.streamsense.analyticsservice.storage.InMemoryLogoObjectStore;
import com.streamsense.analyticsservice.storage.LogoObjectStore;
import com.streamsense.analyticsservice.storage.S3LogoObjectStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Which {@link LogoObjectStore} runs: {@code streamsense.logos.store=s3} (the default, and what
 * Compose and Kubernetes run; the endpoint and credentials are then required, so a half-configured
 * deployment fails at start-up rather than on the first upload) or {@code memory} for tests.
 */
@Configuration
public class LogoStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.logos", name = "store", havingValue = "memory")
    public LogoObjectStore inMemoryLogoObjectStore(StreamSenseProperties properties) {
        return new InMemoryLogoObjectStore(properties.getLogos().getBucket());
    }

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.logos", name = "store", havingValue = "s3", matchIfMissing = true)
    public LogoObjectStore s3LogoObjectStore(StreamSenseProperties properties) {
        return new S3LogoObjectStore(properties.getLogos());
    }
}
