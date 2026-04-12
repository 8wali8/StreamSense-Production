package com.streamsense.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import com.streamsense.apigateway.support.TestJwtTokens;

class JwtAuthTokenValidatorTest {

    private JwtAuthTokenValidator validator;
    private GatewayEdgeProperties.Auth authProperties;

    @BeforeEach
    void setUp() {
        validator = new JwtAuthTokenValidator(
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-04-11T12:00:00Z"), ZoneOffset.UTC));
        authProperties = new GatewayEdgeProperties.Auth();
        authProperties.setRequiredIssuer("streamsense-local");
        authProperties.setRequiredAudience("streamsense-clients");
    }

    @Test
    void validatesWellFormedToken() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer " + TestJwtTokens.token("demo-user", "streamsense-local", List.of("streamsense-clients"), Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(), null, "HS256"),
                authProperties);

        assertThat(result.valid()).isTrue();
        assertThat(result.subject()).isEqualTo("demo-user");
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
                "Bearer " + TestJwtTokens.token("demo-user", "streamsense-local", List.of("streamsense-clients"), Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(), null, "none"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_jwt_algorithm");
    }

    @Test
    void rejectsWrongAudience() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer " + TestJwtTokens.token("demo-user", "streamsense-local", List.of("other-clients"), Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(), null, "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("invalid_audience");
    }

    @Test
    void rejectsExpiredToken() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer " + TestJwtTokens.token("demo-user", "streamsense-local", List.of("streamsense-clients"), Instant.parse("2026-04-11T11:59:00Z").getEpochSecond(), null, "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("token_expired");
    }

    @Test
    void rejectsFutureNotBefore() {
        JwtAuthTokenValidator.ValidationResult result = validator.validate(
                "Bearer " + TestJwtTokens.token("demo-user", "streamsense-local", List.of("streamsense-clients"), Instant.parse("2026-04-11T12:10:00Z").getEpochSecond(), Instant.parse("2026-04-11T12:01:00Z").getEpochSecond(), "HS256"),
                authProperties);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("token_not_yet_valid");
    }
}
