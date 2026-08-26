package com.streamsense.apigateway.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;

import com.streamsense.apigateway.auth.JwtAuthTokenValidator;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

// Counterpart of GatewayAuthWebFilter for subscriptions: the handshake carries no usable headers from a browser, so
// the bearer token is read from the graphql-transport-ws connection_init payload (the frontend already sends it as
// connectionParams.Authorization). A rejected init closes the socket before any operation can run, and an accepted
// one is closed again when the token expires so a long-lived socket cannot outlive its credential.
@Component
public class GatewayWebSocketAuthInterceptor implements WebSocketGraphQlInterceptor {

    static final String SUBJECT_ATTRIBUTE = "streamsense.auth.subject";
    static final String EXPIRY_TIMER_ATTRIBUTE = "streamsense.auth.expiryTimer";
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4401, "token_expired");

    private static final Logger log = LoggerFactory.getLogger(GatewayWebSocketAuthInterceptor.class);

    private final GatewayEdgeProperties properties;
    private final JwtAuthTokenValidator tokenValidator;
    private final MeterRegistry meterRegistry;
    private final WebSocketSessionRegistry sessionRegistry;
    private final String graphqlWebSocketPath;
    private final Clock clock;

    @Autowired
    public GatewayWebSocketAuthInterceptor(
            GatewayEdgeProperties properties,
            JwtAuthTokenValidator tokenValidator,
            MeterRegistry meterRegistry,
            WebSocketSessionRegistry sessionRegistry,
            @Value("${spring.graphql.websocket.path:/graphql}") String graphqlWebSocketPath) {
        this(properties, tokenValidator, meterRegistry, sessionRegistry, graphqlWebSocketPath, Clock.systemUTC());
    }

    GatewayWebSocketAuthInterceptor(
            GatewayEdgeProperties properties,
            JwtAuthTokenValidator tokenValidator,
            MeterRegistry meterRegistry,
            WebSocketSessionRegistry sessionRegistry,
            String graphqlWebSocketPath,
            Clock clock) {
        this.properties = properties;
        this.tokenValidator = tokenValidator;
        this.meterRegistry = meterRegistry;
        this.sessionRegistry = sessionRegistry;
        this.graphqlWebSocketPath = graphqlWebSocketPath;
        this.clock = clock;
    }

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo sessionInfo,
            Map<String, Object> connectionInitPayload) {
        GatewayEdgeProperties.Auth auth = properties.getAuth();
        // Same policy as HTTP: only enforce when the websocket path itself is protected and not excluded.
        if (!auth.isEnabled() || !auth.protects(graphqlWebSocketPath)) {
            return Mono.empty();
        }

        JwtAuthTokenValidator.ValidationResult result = tokenValidator.validate(
                authorizationFrom(connectionInitPayload), auth);

        if (result.valid()) {
            sessionInfo.getAttributes().put(SUBJECT_ATTRIBUTE, result.subject());
            scheduleExpiry(sessionInfo, result.expiresAt());
            return Mono.empty();
        }

        meterRegistry.counter("streamsense_gateway_auth_rejections_total", "reason", result.reason()).increment();
        return Mono.error(new UnauthorizedConnectionException(result.reason()));
    }

    @Override
    public void handleConnectionClosed(WebSocketSessionInfo sessionInfo, int statusCode,
            Map<String, Object> connectionInitPayload) {
        Object timer = sessionInfo.getAttributes().remove(EXPIRY_TIMER_ATTRIBUTE);
        if (timer instanceof Disposable disposable) {
            disposable.dispose();
        }
    }

    private void scheduleExpiry(WebSocketSessionInfo sessionInfo, Instant expiresAt) {
        if (expiresAt == null) {
            return;
        }
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        Disposable timer = Mono.delay(remaining.isNegative() ? Duration.ZERO : remaining)
                .subscribe(tick -> {
                    // The close must outlive this timer: the closing connection triggers handleConnectionClosed,
                    // which disposes the timer, and cancelling an in-flight sendClose would drop the close frame.
                    sessionInfo.getAttributes().remove(EXPIRY_TIMER_ATTRIBUTE);
                    log.info("closing websocket session id={} subject={}: token expired",
                            sessionInfo.getId(), sessionInfo.getAttributes().get(SUBJECT_ATTRIBUTE));
                    meterRegistry.counter("streamsense_gateway_auth_rejections_total", "reason", "token_expired")
                            .increment();
                    sessionRegistry.close(sessionInfo.getId(), TOKEN_EXPIRED)
                            .subscribe(ignored -> { }, error -> log.debug("websocket expiry close failed id={}",
                                    sessionInfo.getId(), error));
                });
        sessionInfo.getAttributes().put(EXPIRY_TIMER_ATTRIBUTE, timer);
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
