package com.streamsense.apigateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class RouteIdHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(exchange.getRequest().mutate().header("X-StreamSense-Route-Id", route.getId()).build())
                .build();

        mutatedExchange.getResponse().getHeaders().set("X-StreamSense-Route-Id", route.getId());
        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }
}
