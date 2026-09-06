package com.streamsense.apigateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Writes an RFC 9457 {@code application/problem+json} body from a WebFilter, which commits the
 * response itself and therefore bypasses WebFlux's problem-details support. The shape matches the
 * REST services: {@code type}, {@code title}, {@code status}, {@code detail}, {@code instance},
 * {@code service}, {@code timestamp}, and the request's {@code correlationId}; filter-specific
 * fields (such as {@code reason} or {@code limit}) are added as extra members.
 */
final class ProblemResponses {

    static final String PROBLEM_TYPE_BASE = "https://streamsense.dev/problems/";

    private static final ObjectMapper JSON = new ObjectMapper();

    private ProblemResponses() {}

    static Mono<Void> write(
            ServerWebExchange exchange,
            HttpStatus status,
            String type,
            String detail,
            String serviceName,
            Map<String, ?> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_TYPE_BASE + type);
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", exchange.getRequest().getPath().value());
        body.put("service", serviceName);
        body.put("timestamp", Instant.now().toString());
        String correlationId =
                exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationIdWebFilter.CORRELATION_ID_HEADER);
        }
        if (correlationId != null && !correlationId.isBlank()) {
            body.put("correlationId", correlationId);
        }
        body.putAll(extra);

        byte[] bytes;
        try {
            bytes = JSON.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"type\":\"" + PROBLEM_TYPE_BASE + type + "\",\"status\":" + status.value() + "}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
