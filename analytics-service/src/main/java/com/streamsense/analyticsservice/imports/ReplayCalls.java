package com.streamsense.analyticsservice.imports;

import java.util.Map;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** The two calls both replay services answer alike: read an import's status, and stop it. 404 means "no such import". */
final class ReplayCalls {

    private static final ParameterizedTypeReference<Map<String, Object>> BODY = new ParameterizedTypeReference<>() {};

    private ReplayCalls() {}

    static Optional<ReplayStatus> status(RestClient restClient, String uri) {
        try {
            Map<String, Object> body = restClient.get().uri(uri).retrieve().body(BODY);
            return body == null ? Optional.empty() : Optional.of(ReplayStatus.from(body));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        }
    }

    static Optional<ReplayStatus> stop(RestClient restClient, String uri) {
        try {
            Map<String, Object> body = restClient.delete().uri(uri).retrieve().body(BODY);
            return body == null ? Optional.empty() : Optional.of(ReplayStatus.from(body));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        }
    }
}
