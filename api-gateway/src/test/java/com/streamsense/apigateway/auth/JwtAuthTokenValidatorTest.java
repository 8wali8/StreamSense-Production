package com.streamsense.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import com.streamsense.apigateway.support.TestJwtTokens;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtAuthTokenValidatorTest {

    private JwtAuthTokenValidator validator;
    private GatewayEdgeProperties.Auth authProperties;

    @BeforeEach
    void setUp() {
        validator = new JwtAuthTokenValidator(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-04-11T12:00:00Z"), ZoneOffset.UTC));
        authProperties = new GatewayEdgeProperties.Auth();
        authProperties.setRequiredIssuer("streamsense-local");
        authProperties.setRequiredAudience("streamsense-clients");
        authProperties.setHmacSecret(TestJwtTokens.TEST_SECRET);
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.tokenSignedWith(
                                "a-different-secret-that-is-also-32-bytes-long",
                                "demo-user",
                                "streamsense-local",
                                List.of("streamsense-clients"),
                                Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(),
                                null,
                                "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_jwt_signature");
    }

    @Test
    void rejectsPayloadsEditedAfterSigning() {
        String[] parts = TestJwtTokens.validToken("demo-user").split("\\.");
        String elevated = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"sub\":\"admin\"}".getBytes(StandardCharsets.UTF_8));

        JwtAuthTokenValidator.ValidationResult result =
                validator.validate("Bearer " + parts[0] + "." + elevated + "." + parts[2], authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_jwt_signature");
    }

    @Test
    void rejectsEveryTokenWhenNoKeyIsConfigured() {
        authProperties.setHmacSecret(null);

        JwtAuthTokenValidator.ValidationResult result =
                validator.validate("Bearer " + TestJwtTokens.validToken("demo-user"), authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("auth_key_not_configured");
    }

    @Test
    void rejectsAlgorithmsTheConfiguredKeyCannotVerify() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.token(
                                "demo-user",
                                "streamsense-local",
                                List.of("streamsense-clients"),
                                Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(),
                                null,
                                "RS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("unsupported_jwt_algorithm");
    }

    @Test
    void acceptsTokensMintedByTheDevTool() {
        // Produced by tools/mint-jwt.py with TEST_SECRET at 2026-04-11T12:00:00Z, ttl 600s; the fixed clock keeps it
        // live.
        String minted = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                + "eyJzdWIiOiJtaW50ZWQtdXNlciIsImlzcyI6InN0cmVhbXNlbnNlLWxvY2FsIiwiYXVkIjoic3RyZWFtc2Vuc2UtY2xpZW50cyIsImlhdCI6MTc3NTkwODgwMCwiZXhwIjoxNzc1OTA5NDAwfQ."
                + "6SNqiGAV9QdX5sKQjUPhQ6ou0kRKROiC2mjx2NpW4pY";

        JwtAuthTokenValidator.ValidationResult result = validator.validate("Bearer " + minted, authProperties);

        assertThat(result.valid()).isTrue();
        assertThat(result.subject()).isEqualTo("minted-user");
    }

    @Test
    void validatesWellFormedToken() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.token(
                                "demo-user",
                                "streamsense-local",
                                List.of("streamsense-clients"),
                                Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(),
                                null,
                                "HS256"),
                authProperties);

        assertThat(result.valid()).isTrue();
        assertThat(result.subject()).isEqualTo("demo-user");
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-04-11T12:10:00Z"));
    }

    @Test
    void rejectsMissingBearerPrefix() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate("demo", authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("missing_bearer_token");
    }

    @Test
    void rejectsNoneAlgorithm() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.token(
                                "demo-user",
                                "streamsense-local",
                                List.of("streamsense-clients"),
                                Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(),
                                null,
                                "none"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_jwt_algorithm");
    }

    @Test
    void rejectsWrongAudience() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.token(
                                "demo-user",
                                "streamsense-local",
                                List.of("other-clients"),
                                Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(),
                                null,
                                "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_audience");
    }

    @Test
    void rejectsExpiredToken() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.token(
                                "demo-user",
                                "streamsense-local",
                                List.of("streamsense-clients"),
                                Instant.parse("2026-04-11T11:59:00Z").getEpochSecond(),
                                null,
                                "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("token_expired");
    }

    @Test
    void rejectsFutureNotBefore() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer "
                        + TestJwtTokens.token(
                                "demo-user",
                                "streamsense-local",
                                List.of("streamsense-clients"),
                                Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(),
                                Instant.parse("2026-04-11T12:01:00Z").getEpochSecond(),
                                "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("token_not_yet_valid");
    }
}
