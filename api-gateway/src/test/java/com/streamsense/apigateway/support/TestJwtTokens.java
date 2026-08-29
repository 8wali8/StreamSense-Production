package com.streamsense.apigateway.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

public final class TestJwtTokens {

    /** At least 32 bytes, as HS256 requires; wired into test properties and signing alike. */
    public static final String TEST_SECRET = "streamsense-test-hmac-secret-0123456789abcdef";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestJwtTokens() {}

    public static String validToken(String subject) {
        return token(
                subject,
                "streamsense-local",
                List.of("streamsense-clients"),
                Instant.now().plusSeconds(600).getEpochSecond(),
                null,
                "HS256");
    }

    public static String expiredToken(String subject) {
        return token(
                subject,
                "streamsense-local",
                List.of("streamsense-clients"),
                Instant.now().minusSeconds(30).getEpochSecond(),
                null,
                "HS256");
    }

    public static String token(
            String subject, String issuer, List<String> audience, long exp, Long nbf, String algorithm) {
        return tokenSignedWith(TEST_SECRET, subject, issuer, audience, exp, nbf, algorithm);
    }

    public static String tokenSignedWith(
            String secret, String subject, String issuer, List<String> audience, long exp, Long nbf, String algorithm) {
        JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(algorithm);
        if (!JWSAlgorithm.Family.HMAC_SHA.contains(jwsAlgorithm)) {
            // "none" and asymmetric algorithms cannot be signed with the shared secret; produce the shape only so
            // tests can exercise the rejection paths.
            return unsignedToken(subject, issuer, audience, exp, nbf, algorithm);
        }
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .expirationTime(new Date(exp * 1000L));
            if (nbf != null) {
                claims.notBeforeTime(new Date(nbf * 1000L));
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(jwsAlgorithm).type(JOSEObjectType.JWT).build(), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static String malformedToken() {
        return "not-a-jwt";
    }

    private static String unsignedToken(
            String subject, String issuer, List<String> audience, long exp, Long nbf, String algorithm) {
        try {
            String header = encodeJson("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
            String payload =
                    encodeJson(OBJECT_MAPPER.writeValueAsString(new JwtPayload(subject, issuer, audience, exp, nbf)));
            return header + "." + payload + ".signature";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encodeJson(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private record JwtPayload(String sub, String iss, List<String> aud, long exp, Long nbf) {}
}
