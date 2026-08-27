package com.streamsense.apigateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

/**
 * Gives every {@link org.springframework.web.reactive.function.client.WebClient} built from the
 * auto-configured builder a bounded connect and response timeout, so a stalled downstream service
 * fails the GraphQL field instead of holding the subscriber forever.
 */
@Configuration
public class DownstreamWebClientConfig {

    @Bean
    public WebClientCustomizer downstreamTimeoutWebClientCustomizer(DownstreamServicesProperties properties) {
        return builder -> builder.clientConnector(new ReactorClientHttpConnector(httpClient(properties)));
    }

    static HttpClient httpClient(DownstreamServicesProperties properties) {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeout().toMillis())
                .responseTimeout(properties.getResponseTimeout());
    }
}
