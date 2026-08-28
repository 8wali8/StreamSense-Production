package com.streamsense.apigateway.graphql;

import java.util.LinkedHashMap;
import java.util.Map;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.data.method.annotation.GraphQlExceptionHandler;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Turns resolver failures into GraphQL errors with a stable {@code extensions.code} instead of
 * the framework's generic "INTERNAL_ERROR" with no explanation.
 *
 * <ul>
 *   <li>{@code DOWNSTREAM_UNAVAILABLE}: the downstream service could not be reached or timed out
 *       (connection refused, DNS failure, the response timeout from branch 03).</li>
 *   <li>{@code DOWNSTREAM_ERROR}: the downstream service answered with a non-2xx status; the
 *       status is in {@code extensions.status}.</li>
 *   <li>{@code BAD_REQUEST}: the arguments failed validation.</li>
 * </ul>
 * Messages never include downstream response bodies.
 */
@ControllerAdvice
public class GraphQlErrorAdvice {

    private static final Logger log = LoggerFactory.getLogger(GraphQlErrorAdvice.class);

    @GraphQlExceptionHandler
    public GraphQLError handleDownstreamUnavailable(WebClientRequestException ex, DataFetchingEnvironment env) {
        String host = ex.getUri() != null ? ex.getUri().getHost() : "unknown";
        log.warn("downstream unavailable field={} host={} cause={}", env.getField().getName(), host, ex.getMessage());
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("Downstream service unavailable")
                .extensions(extensions("DOWNSTREAM_UNAVAILABLE", Map.of("host", host)))
                .build();
    }

    @GraphQlExceptionHandler
    public GraphQLError handleDownstreamError(WebClientResponseException ex, DataFetchingEnvironment env) {
        String host = ex.getRequest() != null && ex.getRequest().getURI() != null
                ? ex.getRequest().getURI().getHost()
                : "unknown";
        log.warn("downstream error field={} host={} status={}", env.getField().getName(), host, ex.getStatusCode().value());
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.INTERNAL_ERROR)
                .message("Downstream service returned an error")
                .extensions(extensions("DOWNSTREAM_ERROR", Map.of("host", host, "status", ex.getStatusCode().value())))
                .build();
    }

    @GraphQlExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class})
    public GraphQLError handleBadRequest(Exception ex, DataFetchingEnvironment env) {
        return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.BAD_REQUEST)
                .message(ex.getMessage() != null ? ex.getMessage() : "Invalid request")
                .extensions(extensions("BAD_REQUEST", Map.of()))
                .build();
    }

    private static Map<String, Object> extensions(String code, Map<String, Object> extra) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", code);
        extensions.putAll(extra);
        return extensions;
    }
}
