package com.streamsense.apigateway.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class TestJwtTokens {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestJwtTokens() {
    }

    public static String validToken(String subject) {
        return token(subject, "streamsense-local", List.of("streamsense-clients"), Instant.now().plusSeconds(600).getEpochSecond(), null, "HS256");
    }

    public static String expiredToken(String subject) {
        return token(subject, "streamsense-local", List.of("streamsense-clients"), Instant.now().minusSeconds(30).getEpochSecond(), null, "HS256");
    }

    public static String token(
            String subject,
            String issuer,
            List<String> audience,
            long exp,
            Long nbf,
            String algorithm) {
        try {
            String header = encodeJson("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
            String payload = encodeJson(OBJECT_MAPPER.writeValueAsString(new JwtPayload(subject, issuer, audience, exp, nbf)));
            return header + "." + payload + ".signature";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String malformedToken() {
        return "not-a-jwt";
    }

    private static String encodeJson(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private record JwtPayload(String sub, String iss, List<String> aud, long exp, Long nbf) {
    }
}
