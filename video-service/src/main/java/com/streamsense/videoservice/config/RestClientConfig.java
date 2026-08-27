package com.streamsense.videoservice.config;

import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    private final StreamSenseProperties properties;

    public RestClientConfig(StreamSenseProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getMl().getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getMl().getReadTimeoutMs()))
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
}
