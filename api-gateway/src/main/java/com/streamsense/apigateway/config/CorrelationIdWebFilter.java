package com.streamsense.apigateway.config;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
public class CorrelationIdWebFilter implements WebFilter {

    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String LEGACY_CORRELATION_ID_HEADER = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = firstNonBlank(
                exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER),
                exchange.getRequest().getHeaders().getFirst(LEGACY_CORRELATION_ID_HEADER),
                UUID.randomUUID().toString());

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        MDC.put(CORRELATION_ID_KEY, correlationId);

        return chain.filter(exchange.mutate().request(request).build())
                .doFinally(signalType -> MDC.remove(CORRELATION_ID_KEY));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
