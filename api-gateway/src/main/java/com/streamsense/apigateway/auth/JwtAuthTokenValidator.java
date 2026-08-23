package com.streamsense.apigateway.auth;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.streamsense.apigateway.config.GatewayEdgeProperties;

@Component
public class JwtAuthTokenValidator {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public JwtAuthTokenValidator(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    JwtAuthTokenValidator(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ValidationResult validate(String authorizationHeader, GatewayEdgeProperties.Auth authProperties) {
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            return ValidationResult.invalid("missing_bearer_token");
        }

        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (!StringUtils.hasText(token)) {
            return ValidationResult.invalid("missing_bearer_token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3 || isBlank(parts[0]) || isBlank(parts[1]) || isBlank(parts[2])) {
            return ValidationResult.invalid("invalid_jwt_shape");
        }

        try {
            JsonNode header = decode(parts[0]);
            JsonNode payload = decode(parts[1]);

            String algorithm = header.path("alg").asText();
            if (!StringUtils.hasText(algorithm) || "none".equalsIgnoreCase(algorithm)) {
                return ValidationResult.invalid("invalid_jwt_algorithm");
            }

            // Nothing in the payload is trusted until the signature checks out against a configured key.
            SignedJWT signedJwt = SignedJWT.parse(token);
            JWSVerifier verifier = JwtSignatureVerifiers.resolve(signedJwt.getHeader().getAlgorithm(), authProperties);
            if (!signedJwt.verify(verifier)) {
                return ValidationResult.invalid("invalid_jwt_signature");
            }

            if (!StringUtils.hasText(payload.path("sub").asText())) {
                return ValidationResult.invalid("missing_subject");
            }

            if (StringUtils.hasText(authProperties.getRequiredIssuer())
                    && !authProperties.getRequiredIssuer().equals(payload.path("iss").asText())) {
                return ValidationResult.invalid("invalid_issuer");
            }

            if (StringUtils.hasText(authProperties.getRequiredAudience())
                    && !containsAudience(payload.path("aud"), authProperties.getRequiredAudience())) {
                return ValidationResult.invalid("invalid_audience");
            }

            Instant now = Instant.now(clock);
            if (!payload.has("exp")) {
                return ValidationResult.invalid("missing_expiration");
            }

            long exp = payload.path("exp").asLong(-1L);
            if (exp <= now.getEpochSecond()) {
                return ValidationResult.invalid("token_expired");
            }

            if (payload.has("nbf") && payload.path("nbf").asLong(Long.MIN_VALUE) > now.getEpochSecond()) {
                return ValidationResult.invalid("token_not_yet_valid");
            }

            return ValidationResult.valid(payload.path("sub").asText());
        } catch (JwtSignatureVerifiers.UnverifiableTokenException exception) {
            return ValidationResult.invalid(exception.reason());
        } catch (ParseException exception) {
            return ValidationResult.invalid("invalid_jwt_shape");
        } catch (JOSEException exception) {
            return ValidationResult.invalid("invalid_jwt_signature");
        } catch (Exception exception) {
            return ValidationResult.invalid("invalid_jwt_payload");
        }
    }

    private JsonNode decode(String part) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(part);
        return objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
    }

    private boolean containsAudience(JsonNode audienceNode, String requiredAudience) {
        if (audienceNode == null || audienceNode.isMissingNode() || audienceNode.isNull()) {
            return false;
        }
        if (audienceNode.isTextual()) {
            return requiredAudience.equals(audienceNode.asText());
        }
        if (audienceNode.isArray()) {
            Iterator<JsonNode> iterator = audienceNode.elements();
            while (iterator.hasNext()) {
                if (requiredAudience.equals(iterator.next().asText())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record ValidationResult(boolean valid, String subject, String reason) {

        public static ValidationResult valid(String subject) {
            return new ValidationResult(true, subject, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, null, reason);
        }
    }
}
