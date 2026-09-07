package com.streamsense.apigateway.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

// Fails closed: an enabled auth gate with no verifiable key would accept forged tokens, which is worse than not
// starting. Runs before the web server binds so a misconfigured gateway never serves a single request.
@Component
public class GatewayAuthStartupCheck {

    static final int MINIMUM_HMAC_SECRET_BYTES = 32;

    private final GatewayEdgeProperties properties;

    public GatewayAuthStartupCheck(GatewayEdgeProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        GatewayEdgeProperties.Auth auth = properties.getAuth();
        if (!auth.isEnabled()) {
            return;
        }
        String secret = auth.getHmacSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("streamsense.gateway.auth.enabled=true but no signing key is configured; "
                    + "set STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET (at least " + MINIMUM_HMAC_SECRET_BYTES
                    + " bytes) or disable auth");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_HMAC_SECRET_BYTES) {
            throw new IllegalStateException("streamsense.gateway.auth.hmac-secret must be at least "
                    + MINIMUM_HMAC_SECRET_BYTES + " bytes for HS256");
        }
    }
}
