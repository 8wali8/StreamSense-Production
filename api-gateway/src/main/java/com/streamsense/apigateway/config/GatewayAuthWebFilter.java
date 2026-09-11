package com.streamsense.apigateway.config;

import com.streamsense.apigateway.auth.AuthScope;
import com.streamsense.apigateway.auth.JwtAuthTokenValidator;
import com.streamsense.apigateway.graphql.ShareLinkInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayAuthWebFilter implements WebFilter {

    private final GatewayEdgeProperties properties;
    private final JwtAuthTokenValidator tokenValidator;
    private final MeterRegistry meterRegistry;
    private final String graphqlWebSocketPath;
    private final String serviceName;

    public GatewayAuthWebFilter(
            GatewayEdgeProperties properties,
            JwtAuthTokenValidator tokenValidator,
            MeterRegistry meterRegistry,
            @Value("${spring.graphql.websocket.path:/graphql}") String graphqlWebSocketPath,
            @Value("${spring.application.name:api-gateway}") String serviceName) {
        this.serviceName = serviceName;
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

        JwtAuthTokenValidator.ValidationResult result =
                tokenValidator.validate(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION), auth);

        if (result.valid()) {
            exchange.getResponse().getHeaders().set("X-StreamSense-Auth-Subject", result.subject());
            AuthScope scope = new AuthScope(result.subject(), result.role());
            exchange.getAttributes().put(AuthScope.ATTRIBUTE, scope);
            // A signed-in streamer may read their own channel and manage their own deals, but not steer the
            // pipeline and not look at another channel. Routes that name the channel by id are checked by
            // analytics-service from the scope headers; GraphQL by ChannelScopeInterceptor and the resolvers.
            if (!scope.isOperator()) {
                if (auth.requiresOperator(exchange.getRequest().getMethod(), path)) {
                    return forbidden(exchange, "operator_required", "This action needs an operator account");
                }
                String channel = AuthScope.restChannel(
                        path, exchange.getRequest().getQueryParams().getFirst("streamer"));
                if (!scope.allows(channel)) {
                    return forbidden(exchange, "channel_forbidden", "This channel is not yours to see");
                }
            }
            return chain.filter(exchange);
        }

        // A share link: no bearer, but a share token on a GraphQL query. ShareLinkInterceptor validates the
        // token against analytics-service and restricts the request to that deal's read-only report queries.
        if (isShareRequest(exchange.getRequest(), path)) {
            return chain.filter(exchange);
        }

        meterRegistry
                .counter("streamsense_gateway_auth_rejections_total", "reason", result.reason())
                .increment();
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        // A problem+json body like every other StreamSense error; `error` and `reason` stay for existing clients.
        return ProblemResponses.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Authentication failed: " + result.reason(),
                serviceName,
                Map.of("error", "unauthorized", "reason", result.reason()));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String reason, String detail) {
        meterRegistry
                .counter("streamsense_gateway_auth_rejections_total", "reason", reason)
                .increment();
        return ProblemResponses.write(
                exchange, HttpStatus.FORBIDDEN, "forbidden", detail, serviceName, Map.of("reason", reason));
    }

    private boolean isShareRequest(ServerHttpRequest request, String path) {
        String token = request.getHeaders().getFirst(ShareLinkInterceptor.HEADER);
        return path.equals(graphqlWebSocketPath)
                && HttpMethod.POST.equals(request.getMethod())
                && token != null
                && !token.isBlank();
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
