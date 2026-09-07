package com.streamsense.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.ReadTimeoutException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.test.StepVerifier;

class DownstreamWebClientTimeoutTest {

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
    void stalledDownstreamFailsAfterTheConfiguredResponseTimeout() {
        server.enqueue(new MockResponse().setBody("[]").setHeadersDelay(2, TimeUnit.SECONDS));
        DownstreamServicesProperties properties = new DownstreamServicesProperties();
        properties.setResponseTimeout(Duration.ofMillis(200));

        WebClient client = customizedBuilder(properties).baseUrl(server.url("/").toString()).build();

        StepVerifier.create(client.get().uri("/api/recommendations").retrieve().bodyToMono(String.class))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(WebClientRequestException.class);
                    assertThat(error.getCause()).isInstanceOf(ReadTimeoutException.class);
                })
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void responsiveDownstreamStillSucceeds() {
        server.enqueue(new MockResponse().setBody("[]").addHeader("Content-Type", "application/json"));
        DownstreamServicesProperties properties = new DownstreamServicesProperties();

        WebClient client = customizedBuilder(properties).baseUrl(server.url("/").toString()).build();

        StepVerifier.create(client.get().uri("/api/recommendations").retrieve().bodyToMono(String.class))
                .expectNext("[]")
                .verifyComplete();
    }

    private static WebClient.Builder customizedBuilder(DownstreamServicesProperties properties) {
        WebClient.Builder builder = WebClient.builder();
        new DownstreamWebClientConfig().downstreamTimeoutWebClientCustomizer(properties).customize(builder);
        return builder;
    }
}
