package com.streamsense.recommendationservice.config;

import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bounds every {@link org.springframework.web.client.RestClient} built from the auto-configured
 * builder with a connect and read timeout, so a slow sentiment-service or video-service cannot
 * hold a recommendation request thread indefinitely.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer downstreamTimeoutRestClientCustomizer(StreamSenseProperties properties) {
        StreamSenseProperties.Services services = properties.getServices();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(services.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(services.getReadTimeoutMs()));
        return builder ->
                builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
