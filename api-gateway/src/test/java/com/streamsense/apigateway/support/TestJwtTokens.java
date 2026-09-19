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

    /** The role every token here carries unless a test asks for another: the widest scope, so nothing is refused for it. */
    public static final String DEFAULT_ROLE = "operator";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private TestJwtTokens() {}

    /** A token as Twitch sign-in mints it: the login as subject, plus the role claim. */
    public static String tokenWithRole(String login, String role) {
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    new JWTClaimsSet.Builder()
                            .subject(login)
                            .issuer("streamsense-local")
                            .audience(List.of("streamsense-clients"))
                            .expirationTime(
                                    new Date(Instant.now().plusSeconds(600).toEpochMilli()))
                            .claim("login", login)
                            .claim("role", role)
                            .build());
            jwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** Well signed and in date, but without a role: the shape the retired access links had, refused since. */
    public static String tokenWithoutRole(String subject) {
        return signed(
                TEST_SECRET,
                subject,
                "streamsense-local",
                List.of("streamsense-clients"),
                Instant.now().plusSeconds(600).getEpochSecond(),
                null,
                JWSAlgorithm.HS256,
                null);
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

    /** A token with the given registered claims and the default role. */
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
        return signed(secret, subject, issuer, audience, exp, nbf, jwsAlgorithm, DEFAULT_ROLE);
    }

    public static String malformedToken() {
        return "not-a-jwt";
    }

    private static String signed(
            String secret,
            String subject,
            String issuer,
            List<String> audience,
            long exp,
            Long nbf,
            JWSAlgorithm algorithm,
            String role) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .expirationTime(new Date(exp * 1000L));
            if (nbf != null) {
                claims.notBeforeTime(new Date(nbf * 1000L));
            }
            if (role != null) {
                claims.claim("role", role);
            }
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(algorithm).type(JOSEObjectType.JWT).build(), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String unsignedToken(
            String subject, String issuer, List<String> audience, long exp, Long nbf, String algorithm) {
        try {
            String header = encodeJson("{\"alg\":\"" + algorithm + "\",\"typ\":\"JWT\"}");
            String payload = encodeJson(OBJECT_MAPPER.writeValueAsString(
                    new JwtPayload(subject, issuer, audience, exp, nbf, DEFAULT_ROLE)));
            return header + "." + payload + ".signature";
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String encodeJson(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private record JwtPayload(String sub, String iss, List<String> aud, long exp, Long nbf, String role) {}
}
