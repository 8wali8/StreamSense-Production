package com.streamsense.apigateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Addresses and timeouts for every service the gateway talks to.
 *
 * <p>Every base URL is required: the gateway refuses to start when one is missing instead of
 * silently falling back to {@code localhost}, which only ever worked on a developer machine.
 * The timeouts apply to the {@link org.springframework.web.reactive.function.client.WebClient}
 * calls made from the GraphQL resolvers; the proxied routes use
 * {@code spring.cloud.gateway.server.webflux.httpclient.*} instead.
 */
@Validated
@ConfigurationProperties(prefix = "streamsense.services")
public class DownstreamServicesProperties {

    @Valid
    private final Service chatService = new Service();

    @Valid
    private final Service recommendationService = new Service();

    @Valid
    private final Service sentimentService = new Service();

    @Valid
    private final Service videoService = new Service();

    @Valid
    private final Service videoCaptureService = new Service();

    @Valid
    private final Service analyticsService = new Service();
    /** ml-engine, reached through the proxied {@code /ml/segment} route rather than a resolver. */
    @Valid
    private final Service mlEngine = new Service();

    /** Time allowed to establish the TCP connection to a downstream service. */
    @NotNull
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** Time allowed between sending a request and receiving its response. */
    @NotNull
    private Duration responseTimeout = Duration.ofSeconds(5);

    public Service getChatService() {
        return chatService;
    }

    public Service getRecommendationService() {
        return recommendationService;
    }

    public Service getSentimentService() {
        return sentimentService;
    }

    public Service getVideoService() {
        return videoService;
    }

    public Service getVideoCaptureService() {
        return videoCaptureService;
    }

    public Service getAnalyticsService() {
        return analyticsService;
    }

    public Service getMlEngine() {
        return mlEngine;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public static class Service {

        @NotBlank
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
