package com.streamsense.apigateway.config;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.streamsense.apigateway.ratelimit.InMemoryRateLimiter;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GatewayRateLimitWebFilter implements WebFilter {

    private static final int MAX_CLIENT_KEY_LENGTH = 128;

    private final GatewayEdgeProperties properties;
    private final InMemoryRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final RemoteAddressResolver addressResolver;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayRateLimitWebFilter(
            GatewayEdgeProperties properties,
            InMemoryRateLimiter rateLimiter,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        // X-Forwarded-For is client-controlled unless a proxy we operate appended it, so it is only consulted when
        // trusted hops are configured, and then only the entry the nearest trusted proxy added (read from the right).
        this.addressResolver = properties.getTrustedProxyHops() > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(properties.getTrustedProxyHops())
                : new RemoteAddressResolver() { };
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!properties.isRateLimitEnabled()) {
            return chain.filter(exchange);
        }

        GatewayEdgeProperties.RateLimitRule matchingRule = findMatchingRule(exchange);
        if (matchingRule == null) {
            return chain.filter(exchange);
        }

        String clientKey = resolveClientKey(exchange);
        InMemoryRateLimiter.RateLimitDecision decision = rateLimiter.acquire(
                matchingRule.getId() + ":" + clientKey,
                matchingRule.getRequests(),
                matchingRule.getWindowSeconds());

        exchange.getResponse().getHeaders().set("X-RateLimit-Limit", String.valueOf(matchingRule.getRequests()));
        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        exchange.getResponse().getHeaders().set("X-RateLimit-Reset", String.valueOf(decision.resetAtEpochSeconds()));

        if (decision.allowed()) {
            return chain.filter(exchange);
        }

        meterRegistry.counter(
                "streamsense_gateway_rate_limit_rejections_total",
                "limit", matchingRule.getId(),
                "path", matchingRule.getPath())
                .increment();

        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(matchingRule.getWindowSeconds()));

        String responseBody = "{\"error\":\"rate_limited\",\"limit\":\"" + matchingRule.getId() + "\"}";
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                .bufferFactory()
                .wrap(responseBody.getBytes(StandardCharsets.UTF_8))));
    }

    private GatewayEdgeProperties.RateLimitRule findMatchingRule(ServerWebExchange exchange) {
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "GET";
        String path = exchange.getRequest().getPath().value();
        List<GatewayEdgeProperties.RateLimitRule> rules = properties.getRateLimits();
        for (GatewayEdgeProperties.RateLimitRule rule : rules) {
            if (method.equalsIgnoreCase(rule.getMethod()) && pathMatcher.match(rule.getPath(), path)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveClientKey(ServerWebExchange exchange) {
        InetSocketAddress address = addressResolver.resolve(exchange);
        if (address == null) {
            return "anonymous";
        }
        String host = address.getAddress() != null ? address.getAddress().getHostAddress() : address.getHostString();
        if (host == null || host.isBlank()) {
            return "anonymous";
        }
        return host.length() > MAX_CLIENT_KEY_LENGTH ? host.substring(0, MAX_CLIENT_KEY_LENGTH) : host;
    }
}
