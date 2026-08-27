package com.streamsense.apigateway.config;

import java.util.Map;

import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.stereotype.Component;

import com.streamsense.apigateway.auth.JwtAuthTokenValidator;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

// Counterpart of GatewayAuthWebFilter for subscriptions: the handshake carries no usable headers from a browser, so
// the bearer token is read from the graphql-transport-ws connection_init payload (the frontend already sends it as
// connectionParams.Authorization) and a rejected init closes the socket before any operation can run.
@Component
public class GatewayWebSocketAuthInterceptor implements WebSocketGraphQlInterceptor {

    static final String SUBJECT_ATTRIBUTE = "streamsense.auth.subject";

    private final GatewayEdgeProperties properties;
    private final JwtAuthTokenValidator tokenValidator;
    private final MeterRegistry meterRegistry;

    public GatewayWebSocketAuthInterceptor(
            GatewayEdgeProperties properties,
            JwtAuthTokenValidator tokenValidator,
            MeterRegistry meterRegistry) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo sessionInfo,
            Map<String, Object> connectionInitPayload) {
        GatewayEdgeProperties.Auth auth = properties.getAuth();
        if (!auth.isEnabled()) {
            return Mono.empty();
        }

        JwtAuthTokenValidator.ValidationResult result = tokenValidator.validate(
                authorizationFrom(connectionInitPayload), auth);

        if (result.valid()) {
            sessionInfo.getAttributes().put(SUBJECT_ATTRIBUTE, result.subject());
            return Mono.empty();
        }

        meterRegistry.counter("streamsense_gateway_auth_rejections_total", "reason", result.reason()).increment();
        return Mono.error(new UnauthorizedConnectionException(result.reason()));
    }

    private static String authorizationFrom(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object value = payload.get("Authorization");
        if (value == null) {
            value = payload.get("authorization");
        }
        return value instanceof String header ? header : null;
    }

    static final class UnauthorizedConnectionException extends RuntimeException {

        UnauthorizedConnectionException(String reason) {
            super("websocket connection rejected: " + reason);
        }
    }
}
