package com.streamsense.apigateway.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
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
    private final String graphqlWebSocketPath;

    public GatewayAuthWebFilter(
            GatewayEdgeProperties properties,
            JwtAuthTokenValidator tokenValidator,
            MeterRegistry meterRegistry,
            @Value("${spring.graphql.websocket.path:/graphql}") String graphqlWebSocketPath) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.meterRegistry = meterRegistry;
        this.graphqlWebSocketPath = graphqlWebSocketPath;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewayEdgeProperties.Auth auth = properties.getAuth();
        String path = exchange.getRequest().getPath().value();

        if (!auth.isEnabled() || !auth.protects(path)) {
            return chain.filter(exchange);
        }

        // Browsers cannot set headers on a WebSocket handshake, so the token for subscriptions arrives in the
        // graphql-transport-ws connection_init payload and is enforced by GatewayWebSocketAuthInterceptor instead.
        if (isGraphqlWebSocketHandshake(exchange.getRequest(), path)) {
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

    // Only a GET with the RFC 6455 upgrade headers is a handshake. The HTTP GraphQL endpoint is POST-only and a GET
    // on the websocket path reaches nothing but the handshake handler, so a spoofed Upgrade header on a POST still
    // has to present a valid token here.
    private boolean isGraphqlWebSocketHandshake(ServerHttpRequest request, String path) {
        HttpHeaders headers = request.getHeaders();
        return path.equals(graphqlWebSocketPath)
                && HttpMethod.GET.equals(request.getMethod())
                && "websocket".equalsIgnoreCase(headers.getUpgrade())
                && headers.containsKey("Sec-WebSocket-Key");
    }
}
