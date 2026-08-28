package com.streamsense.apigateway.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketSession;

import reactor.core.publisher.Mono;

// Spring GraphQL only hands interceptors a WebSocketSessionInfo, which cannot close the socket; the handler bean in
// GatewayGraphQlWebSocketConfig registers each live session here so auth can end it by id when its token expires.
@Component
public class WebSocketSessionRegistry {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public Mono<Void> close(String sessionId, CloseStatus status) {
        WebSocketSession session = sessions.get(sessionId);
        return session == null ? Mono.empty() : session.close(status);
    }

    int size() {
        return sessions.size();
    }
}
