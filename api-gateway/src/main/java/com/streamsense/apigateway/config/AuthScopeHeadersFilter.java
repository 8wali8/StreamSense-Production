package com.streamsense.apigateway.config;

import com.streamsense.apigateway.auth.AuthScope;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Tells the proxied services who the request is for. The two headers are set from the scope the auth
 * filter established and never from the client: anything a client sent under those names is dropped
 * first, so a service can trust them as long as it is only reachable through the gateway.
 */
@Component
public class AuthScopeHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        AuthScope scope = exchange.getAttribute(AuthScope.ATTRIBUTE);
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    headers.remove(AuthScope.LOGIN_HEADER);
                    headers.remove(AuthScope.ROLE_HEADER);
                    if (scope != null) {
                        headers.set(AuthScope.LOGIN_HEADER, scope.login());
                        if (scope.role() != null) {
                            headers.set(AuthScope.ROLE_HEADER, scope.role());
                        }
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
