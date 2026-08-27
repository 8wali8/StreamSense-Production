package com.streamsense.apigateway.config;

import org.slf4j.MDC;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import io.micrometer.context.ContextRegistry;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@Configuration
public class CorrelationIdPropagationConfig {

    static {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                CorrelationIdWebFilter.CORRELATION_ID_KEY,
                () -> MDC.get(CorrelationIdWebFilter.CORRELATION_ID_KEY),
                value -> MDC.put(CorrelationIdWebFilter.CORRELATION_ID_KEY, value),
                () -> MDC.remove(CorrelationIdWebFilter.CORRELATION_ID_KEY));
        // Restores registered thread-locals (this one plus Micrometer's traceId/spanId) from the Reactor Context
        // around every operator, so log lines in handlers and WebClient callbacks carry the right ids.
        Hooks.enableAutomaticContextPropagation();
    }

    @Bean
    public WebClientCustomizer correlationIdWebClientCustomizer() {
        return builder -> builder.filter(forwardCorrelationId());
    }

    // Proxied routes already carry the header via the mutated request; WebClient calls made from GraphQL
    // resolvers need it copied out of the Reactor Context explicitly.
    static ExchangeFilterFunction forwardCorrelationId() {
        return (request, next) -> Mono.deferContextual(context -> {
            String correlationId = context.getOrDefault(CorrelationIdWebFilter.CORRELATION_ID_KEY,
                    MDC.get(CorrelationIdWebFilter.CORRELATION_ID_KEY));
            if (correlationId == null || request.headers().containsKey(CorrelationIdWebFilter.CORRELATION_ID_HEADER)) {
                return next.exchange(request);
            }
            return next.exchange(ClientRequest.from(request)
                    .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, correlationId)
                    .build());
        });
    }
}
