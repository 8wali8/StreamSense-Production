package com.streamsense.recommendationservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class DownstreamRestClientTimeoutTest {

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    void stalledDownstreamFailsAfterTheConfiguredReadTimeout() {
        server.enqueue(new MockResponse().setBody("[]").setHeadersDelay(2, TimeUnit.SECONDS));
        StreamSenseProperties properties = new StreamSenseProperties();
        properties.getServices().setReadTimeoutMs(200);

        RestClient client = customizedBuilder(properties).baseUrl(server.url("/").toString()).build();

        assertThatThrownBy(() -> client.get().uri("/api/sentiment/recent").retrieve().body(String.class))
                .isInstanceOf(ResourceAccessException.class)
                .hasCauseInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void responsiveDownstreamStillSucceeds() {
        server.enqueue(new MockResponse().setBody("[]").addHeader("Content-Type", "application/json"));
        StreamSenseProperties properties = new StreamSenseProperties();

        RestClient client = customizedBuilder(properties).baseUrl(server.url("/").toString()).build();

        assertThat(client.get().uri("/api/sentiment/recent").retrieve().body(String.class)).isEqualTo("[]");
    }

    private static RestClient.Builder customizedBuilder(StreamSenseProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        new RestClientConfig().downstreamTimeoutRestClientCustomizer(properties).customize(builder);
        return builder;
    }
}
