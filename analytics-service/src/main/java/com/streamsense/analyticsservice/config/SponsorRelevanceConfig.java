package com.streamsense.analyticsservice.config;

import com.streamsense.analyticsservice.relevance.SponsorRelevancePointer;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** The bounded client that points sentiment-service at a deal's sponsor; off when no base URL is set. */
@Configuration
public class SponsorRelevanceConfig {

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.services.sentiment-service", name = "base-url")
    public SponsorRelevancePointer sponsorRelevancePointer(
            RestClient.Builder builder, StreamSenseProperties properties) {
        StreamSenseProperties.SentimentService sentiment =
                properties.getServices().getSentimentService();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(sentiment.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(sentiment.getReadTimeoutMs()));
        RestClient.Builder bounded = builder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
        return new SponsorRelevancePointer(bounded, sentiment);
    }
}
