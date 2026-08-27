package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.apigateway.support.TestJwtTokens;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;

/**
 * Speaks graphql-transport-ws directly so the connection_init payload can be controlled, which the test tester
 * does not expose.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "streamsense.services.video-service.base-url=http://localhost:8084",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=api-gateway-test-group-ws-auth",
        "spring.graphql.websocket.path=/graphql",
        "streamsense.gateway.auth.enabled=true",
        "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
})
class GatewayWebSocketAuthIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Test
    void acceptsConnectionInitCarryingAValidBearerToken() {
        Session session = connect(Map.of("Authorization", "Bearer " + TestJwtTokens.validToken("demo-user")), true);

        assertThat(session.messages()).anySatisfy(message -> assertThat(message).contains("\"connection_ack\""));
        assertThat(session.messages()).anySatisfy(message -> assertThat(message).contains("\"health\":\"ok\""));
    }

    @Test
    void closesTheSocketWhenConnectionInitHasNoToken() {
        Session session = connect(Map.of(), false);

        assertThat(session.messages()).noneSatisfy(message -> assertThat(message).contains("\"connection_ack\""));
        assertThat(session.closeStatus()).isNotNull();
        assertThat(session.closeStatus().getCode()).isEqualTo(4401);
    }

    @Test
    void closesTheSocketWhenTheTokenIsSignedWithAnotherKey() {
        String forged = TestJwtTokens.tokenSignedWith("another-secret-that-is-at-least-32-bytes-long", "demo-user",
                "streamsense-local", List.of("streamsense-clients"),
                java.time.Instant.now().plusSeconds(600).getEpochSecond(), null, "HS256");

        Session session = connect(Map.of("Authorization", "Bearer " + forged), false);

        assertThat(session.messages()).noneSatisfy(message -> assertThat(message).contains("\"connection_ack\""));
        assertThat(session.closeStatus().getCode()).isEqualTo(4401);
    }

    /**
     * Sends connection_init with the given payload; when an ack is expected, follows it with a health query and
     * closes once the server reports the operation complete. Returns everything the server sent plus the close status.
     */
    private Session connect(Map<String, Object> initPayload, boolean queryAfterAck) {
        List<String> messages = new CopyOnWriteArrayList<>();
        AtomicReference<CloseStatus> closeStatus = new AtomicReference<>();
        String init = json(Map.of("type", "connection_init", "payload", initPayload));
        String subscribe = json(Map.of("id", "1", "type", "subscribe", "payload", Map.of("query", "{ health }")));

        // The subprotocol must be negotiated by the client handshaker itself, not sent as a raw header.
        new ReactorNettyWebSocketClient(HttpClient.create(),
                () -> WebsocketClientSpec.builder().protocols("graphql-transport-ws"))
                .execute(URI.create("ws://localhost:" + port + "/graphql"), session -> {
                    session.closeStatus().subscribe(closeStatus::set);
                    return session.send(Mono.just(session.textMessage(init)))
                            .thenMany(session.receive()
                                    .map(WebSocketMessage::getPayloadAsText)
                                    .doOnNext(messages::add)
                                    .flatMap(message -> {
                                        if (queryAfterAck && message.contains("\"connection_ack\"")) {
                                            return session.send(Mono.just(session.textMessage(subscribe)));
                                        }
                                        if (message.contains("\"complete\"")) {
                                            return session.close();
                                        }
                                        return Mono.empty();
                                    }))
                            .then();
                })
                .block(TIMEOUT);

        return new Session(messages, closeStatus.get());
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Session(List<String> messages, CloseStatus closeStatus) {
    }
}
