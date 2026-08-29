package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.apigateway.support.GraphqlTransportWsProbe;
import com.streamsense.apigateway.support.GraphqlTransportWsProbe.Session;
import com.streamsense.apigateway.support.TestJwtTokens;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * Auth is enabled but only /api/** is protected, so the websocket path follows the same policy as HTTP GraphQL
 * and accepts connection_init without a token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events",
            "streamsense.topics.sponsorDetections=stream.sponsor.detections",
            "streamsense.services.sentiment-service.base-url=http://localhost:8083",
            "streamsense.services.video-service.base-url=http://localhost:8084",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group-ws-unprotected",
            "spring.graphql.websocket.path=/graphql",
            "streamsense.gateway.auth.enabled=true",
            "streamsense.gateway.auth.protected-paths=/api/**",
            "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET
        })
class GatewayWebSocketAuthUnprotectedPathIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void acceptsConnectionInitWithoutATokenWhenTheWebSocketPathIsNotProtected() {
        Session session = GraphqlTransportWsProbe.connect(port, Map.of(), true, Duration.ofSeconds(15));

        assertThat(session.acknowledged()).isTrue();
        assertThat(session.messages()).anySatisfy(message -> assertThat(message).contains("\"health\":\"ok\""));
    }
}
