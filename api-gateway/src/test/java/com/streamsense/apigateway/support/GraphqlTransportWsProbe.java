package com.streamsense.apigateway.support;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;

/**
 * Minimal graphql-transport-ws client for tests: the tester API does not expose the connection_init payload, which
 * is exactly what the WebSocket auth path keys on.
 */
public final class GraphqlTransportWsProbe {

    private static final ObjectMapper JSON = new ObjectMapper();

    private GraphqlTransportWsProbe() {
    }

    public record Session(List<String> messages, CloseStatus closeStatus) {

        public boolean acknowledged() {
            return messages.stream().anyMatch(message -> message.contains("\"connection_ack\""));
        }
    }

    /**
     * Sends connection_init with the given payload. When {@code queryAfterAck} is set, follows the ack with a
     * {@code { health }} query and closes once the server reports it complete; otherwise the session stays open until
     * the server closes it or the timeout elapses. Returns everything the server sent plus the close status.
     */
    public static Session connect(int port, Map<String, Object> initPayload, boolean queryAfterAck, Duration timeout) {
        List<String> messages = new CopyOnWriteArrayList<>();
        AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
        String init = json(Map.of("type", "connection_init", "payload", initPayload));
        String subscribe = json(Map.of("id", "1", "type", "subscribe", "payload", Map.of("query", "{ health }")));

        // The subprotocol must be negotiated by the client handshaker itself, not sent as a raw header.
        new ReactorNettyWebSocketClient(HttpClient.create(),
                () -> WebsocketClientSpec.builder().protocols("graphql-transport-ws"))
                .execute(URI.create("ws://localhost:" + port + "/graphql"), session -> {
                    Mono<Void> traffic = session.send(Mono.just(session.textMessage(init)))
                            .thenMany(session.receive()
                                    .map(WebSocketMessage::getPayloadAsText)
                                    .doOnNext(messages::add)
                                    .flatMap(message -> {
                                        if (queryAfterAck && message.contains("\"connection_ack\"")) {
                                            return session.send(Mono.just(session.textMessage(subscribe)));
                                        }
                                        if (queryAfterAck && message.contains("\"complete\"")) {
                                            return session.close();
                                        }
                                        return Mono.empty();
                                    }))
                            .then();
                    // The receive stream ends as the socket closes; wait for the close status itself before
                    // completing so callers never observe a null status from a server-initiated close.
                    return traffic.then(session.closeStatus()
                            .doOnNext(closeStatus::set)
                            .timeout(Duration.ofSeconds(5), Mono.empty())
                            .then());
                })
                .block(timeout);

        return new Session(messages, closeStatus.get());
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
