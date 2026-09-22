package com.streamsense.videoservice.config;

import com.streamsense.videoservice.client.CurrentDealClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    private final StreamSenseProperties properties;

    public RestClientConfig(StreamSenseProperties properties) {
        this.properties = properties;
    }

    /** The ml-engine client, on the ML timeouts; primary so the pipeline's callers and tests bind to it by type. */
    @Bean
    @Primary
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.connectTimeout(Duration.ofMillis(properties.getMl().getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(properties.getMl().getReadTimeoutMs()))
                .additionalInterceptors((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY);
                    String traceparent = MDC.get("traceparent");

                    if (StringUtils.hasText(correlationId)) {
                        request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
                    }

                    if (StringUtils.hasText(traceparent)) {
                        request.getHeaders().set("traceparent", traceparent);
                    }

                    return execution.execute(request, body);
                })
                .build();
    }

    /**
     * The current-deal lookup, only when analytics-service has a base URL; without one every frame goes to
     * ml-engine as before. Its own client on its own timeouts, so a slow analytics answer is bounded apart.
     */
    @Bean
    @ConditionalOnProperty(prefix = "streamsense.services.analytics-service", name = "base-url")
    public CurrentDealClient currentDealClient(RestTemplateBuilder builder) {
        StreamSenseProperties.Endpoint endpoint = properties.getServices().getAnalyticsService();
        RestTemplate client = builder.connectTimeout(Duration.ofMillis(endpoint.getConnectTimeoutMs()))
                .readTimeout(Duration.ofMillis(endpoint.getReadTimeoutMs()))
                .build();
        return new CurrentDealClient(
                client, endpoint.getBaseUrl(), endpoint.getCurrentDealCacheSeconds() * 1000L, Clock.systemUTC());
    }
}
