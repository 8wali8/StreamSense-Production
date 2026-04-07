package com.streamsense.sentimentservice.config;

import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .additionalInterceptors((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
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
