package com.streamsense.apigateway.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

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

    private final GatewayEdgeProperties properties;
    private final InMemoryRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayRateLimitWebFilter(
            GatewayEdgeProperties properties,
            InMemoryRateLimiter rateLimiter,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
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
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null && exchange.getRequest().getRemoteAddress().getAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "anonymous";
    }
}
