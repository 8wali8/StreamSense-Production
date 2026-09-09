package com.streamsense.analyticsservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** Token handling and response parsing against a local stand-in for id.twitch.tv and api.twitch.tv. */
class TwitchHelixClientTest {

    private HttpServer server;
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private final List<String> authorizations = new ArrayList<>();
    private boolean rejectFirstStreamsCall;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            int n = tokenRequests.incrementAndGet();
            respond(
                    exchange,
                    200,
                    "{\"access_token\":\"token-" + n + "\",\"expires_in\":3600,\"token_type\":\"bearer\"}");
        });
        server.createContext("/helix/streams", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (rejectFirstStreamsCall) {
                rejectFirstStreamsCall = false;
                respond(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }
            respond(
                    exchange,
                    200,
                    """
                    {"data":[{"id":"41","user_login":"racer","user_name":"Racer","game_name":"Formula 1",
                              "title":"Monza night","viewer_count":1342,"started_at":"2026-09-07T21:27:00Z"},
                             {"id":"","user_login":"broken"}]}
                    """);
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void fetchesOneTokenParsesStreamsAndRefreshesAfterUnauthorized() {
        StreamSenseProperties.Helix helix = new StreamSenseProperties.Helix();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        helix.setClientId("client");
        helix.setClientSecret("secret");
        helix.setBaseUrl(base + "/helix");
        helix.setTokenUrl(base + "/oauth2/token");
        TwitchAppTokenProvider tokens = new TwitchAppTokenProvider(RestClient.builder(), helix, Clock.systemUTC());
        TwitchHelixClient client = new TwitchHelixClient(RestClient.builder(), tokens, helix);

        List<HelixStream> first = client.liveStreams(List.of("racer", "racer", "other"));
        List<HelixStream> second = client.liveStreams(List.of("racer"));

        assertThat(first).hasSize(1);
        assertThat(first.get(0))
                .isEqualTo(new HelixStream("41", "racer", "Monza night", "Formula 1", 1342, 1788816420000L));
        assertThat(second).hasSize(1);
        // The token is fetched once and reused; every Helix call carries it as a bearer.
        assertThat(tokenRequests.get()).isEqualTo(1);
        assertThat(authorizations).containsExactly("Bearer token-1", "Bearer token-1");

        rejectFirstStreamsCall = true;
        assertThat(client.liveStreams(List.of("racer"))).hasSize(1);
        assertThat(tokenRequests.get()).isEqualTo(2);
        assertThat(authorizations).endsWith("Bearer token-1", "Bearer token-2");
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
