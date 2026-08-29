package com.streamsense.apigateway.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.webflux.GraphQlWebSocketHandler;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayGraphQlWebSocketConfig {

    // Takes the place of Boot's @ConditionalOnMissingBean handler (same constructor arguments) so every live
    // session is reachable by id; GatewayWebSocketAuthInterceptor uses that to close a socket when its token expires.
    @Bean
    @ConditionalOnProperty("spring.graphql.websocket.path")
    public GraphQlWebSocketHandler graphQlWebSocketHandler(
            WebGraphQlHandler webGraphQlHandler,
            GraphQlProperties properties,
            ServerCodecConfigurer codecConfigurer,
            WebSocketSessionRegistry sessionRegistry) {
        return new GraphQlWebSocketHandler(
                webGraphQlHandler, codecConfigurer, properties.getWebsocket().getConnectionInitTimeout()) {
            @Override
            public Mono<Void> handle(WebSocketSession session) {
                sessionRegistry.register(session);
                return super.handle(session).doFinally(signal -> sessionRegistry.remove(session.getId()));
            }
        };
    }
}
