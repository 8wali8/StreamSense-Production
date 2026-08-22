package com.streamsense.apigateway.config;

import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

// Ordered first so every later filter (auth, rate limiting) and the handler run inside the correlation context.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdWebFilter implements WebFilter {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
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

        // Carried in the Reactor Context instead of being written to MDC here: the chain hops threads and interleaves
        // requests on one event loop, so a thread-local write at this point ends up labelling other requests' log
        // lines. CorrelationIdPropagationConfig restores it into MDC around each operator on whichever thread runs it.
        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(context -> context.put(CORRELATION_ID_KEY, correlationId));
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
