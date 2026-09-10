package com.streamsense.analyticsservice.config;

import com.streamsense.analyticsservice.imports.CaptureReplayClient;
import com.streamsense.analyticsservice.imports.ChatReplayClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** The bounded clients a VOD import uses to start the chat and capture replays; each exists only with a base URL. */
@Configuration
public class VodImportConfig {

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.services.chat-service", name = "base-url")
    public ChatReplayClient chatReplayClient(RestClient.Builder builder, StreamSenseProperties properties) {
        StreamSenseProperties.Endpoint endpoint = properties.getServices().getChatService();
        return new ChatReplayClient(bounded(builder, endpoint), endpoint.getBaseUrl());
    }

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.services.video-capture-service", name = "base-url")
    public CaptureReplayClient captureReplayClient(RestClient.Builder builder, StreamSenseProperties properties) {
        StreamSenseProperties.Endpoint endpoint = properties.getServices().getVideoCaptureService();
        return new CaptureReplayClient(bounded(builder, endpoint), endpoint.getBaseUrl());
    }

    static RestClient.Builder bounded(RestClient.Builder builder, StreamSenseProperties.Endpoint endpoint) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(endpoint.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(endpoint.getReadTimeoutMs()));
        return builder.clone()
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    }
}
