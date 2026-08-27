package com.streamsense.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=api-gateway-test-group-correlation",
        "streamsense.services.sentiment-service.base-url=http://localhost:8083",
        "streamsense.services.video-service.base-url=http://localhost:8084"
})
class CorrelationIdPropagationIntegrationTest {

    private static final MockWebServer DOWNSTREAM = new MockWebServer();

    @BeforeAll
    static void startServer() throws Exception {
        DOWNSTREAM.start();
    }

    @AfterAll
    static void shutdownServer() throws Exception {
        DOWNSTREAM.shutdown();
    }

    @LocalServerPort
    int port;

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        ProbeController probeController(WebClient.Builder webClientBuilder) {
            return new ProbeController(webClientBuilder.build());
        }
    }

    @RestController
    static class ProbeController {

        private final WebClient webClient;

        ProbeController(WebClient webClient) {
            this.webClient = webClient;
        }

        // Reads MDC after a thread hop: only visible there if the Reactor Context was restored on the new thread.
        @GetMapping("/probe/correlation")
        Mono<String> correlation() {
            return Mono.just("probe")
                    .publishOn(Schedulers.boundedElastic())
                    .map(ignored -> String.valueOf(MDC.get(CorrelationIdWebFilter.CORRELATION_ID_KEY)));
        }

        @GetMapping("/probe/downstream")
        Mono<String> downstream(@RequestParam String url) {
            return webClient.get().uri(url).retrieve().bodyToMono(String.class);
        }
    }

    @Test
    void restoresCorrelationIdOnWorkerThreads() {
        webTestClient().get().uri("/probe/correlation")
                .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "corr-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "corr-123")
                .expectBody(String.class).isEqualTo("corr-123");
    }

    @Test
    void generatesCorrelationIdWhenTheRequestHasNone() {
        EntityExchangeResult<String> result = webTestClient().get().uri("/probe/correlation")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String generated = result.getResponseHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER);
        assertThat(generated).isNotBlank();
        assertThat(result.getResponseBody()).isEqualTo(generated);
    }

    @Test
    void forwardsCorrelationIdOnWebClientCalls() throws Exception {
        DOWNSTREAM.enqueue(new MockResponse().setBody("downstream-ok"));

        webTestClient().get()
                .uri(builder -> builder.path("/probe/downstream")
                        .queryParam("url", DOWNSTREAM.url("/api/downstream").toString())
                        .build())
                .header(CorrelationIdWebFilter.CORRELATION_ID_HEADER, "corr-456")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("downstream-ok");

        RecordedRequest recorded = DOWNSTREAM.takeRequest(5, TimeUnit.SECONDS);
        assertThat(recorded).isNotNull();
        assertThat(recorded.getHeader(CorrelationIdWebFilter.CORRELATION_ID_HEADER)).isEqualTo("corr-456");
    }

    private WebTestClient webTestClient() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
