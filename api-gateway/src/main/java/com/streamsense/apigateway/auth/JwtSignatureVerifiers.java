package com.streamsense.apigateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import java.nio.charset.StandardCharsets;

// Resolves the verifier for a token's algorithm from configuration. Only a shared HMAC secret is wired today; an
// asymmetric issuer (JWKS URI resolved to an RSASSAVerifier/ECDSAVerifier by the token's kid) belongs here as well,
// so neither the validator nor the filters need to know which kind of key is in play.
final class JwtSignatureVerifiers {

    static final int MINIMUM_HMAC_SECRET_BYTES = 32;

    private JwtSignatureVerifiers() {}

    static JWSVerifier resolve(JWSAlgorithm algorithm, GatewayEdgeProperties.Auth auth)
            throws UnverifiableTokenException {
        String secret = auth.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new UnverifiableTokenException("auth_key_not_configured");
        }
        if (!JWSAlgorithm.Family.HMAC_SHA.contains(algorithm)) {
            throw new UnverifiableTokenException("unsupported_jwt_algorithm");
        }
        try {
            return new MACVerifier(secret.getBytes(StandardCharsets.UTF_8));
        } catch (JOSEException exception) {
            throw new UnverifiableTokenException("auth_key_too_short");
        }
    }

    static boolean isUsableHmacSecret(String secret) {
        return secret != null && secret.getBytes(StandardCharsets.UTF_8).length >= MINIMUM_HMAC_SECRET_BYTES;
    }

    static final class UnverifiableTokenException extends Exception {

        private final String reason;

        UnverifiableTokenException(String reason) {
            super(reason);
            this.reason = reason;
        }

        String reason() {
            return reason;
        }
    }
}
