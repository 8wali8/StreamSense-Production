package com.streamsense.apigateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.streamsense.apigateway.auth.JwtAuthTokenValidator;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayAuthWebFilter implements WebFilter {

    private final GatewayEdgeProperties properties;
    private final JwtAuthTokenValidator tokenValidator;
    private final MeterRegistry meterRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthWebFilter(
            GatewayEdgeProperties properties,
            JwtAuthTokenValidator tokenValidator,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewayEdgeProperties.Auth auth = properties.getAuth();
        String path = exchange.getRequest().getPath().value();

        if (!auth.isEnabled() || isExcluded(path, auth.getExcludedPaths()) || !isProtected(path, auth.getProtectedPaths())) {
            return chain.filter(exchange);
        }

        JwtAuthTokenValidator.ValidationResult result = tokenValidator.validate(
                exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION),
                auth);

        if (result.valid()) {
            exchange.getResponse().getHeaders().set("X-StreamSense-Auth-Subject", result.subject());
            return chain.filter(exchange);
        }

        meterRegistry.counter("streamsense_gateway_auth_rejections_total", "reason", result.reason()).increment();
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

        String responseBody = "{\"error\":\"unauthorized\",\"reason\":\"" + result.reason() + "\"}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(responseBody.getBytes(StandardCharsets.UTF_8))));
    }

    private boolean isExcluded(String path, List<String> excludedPaths) {
        return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isProtected(String path, List<String> protectedPaths) {
        return protectedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}
