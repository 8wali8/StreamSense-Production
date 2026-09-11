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
            if (auth.getTwitch().isEnabled()) {
                throw new IllegalStateException(
                        "streamsense.gateway.auth.twitch.enabled=true needs "
                                + "streamsense.gateway.auth.enabled=true: Twitch sign-in mints tokens for a gate that must exist");
            }
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
        verifyTwitch(auth.getTwitch());
    }

    // Sign in with Twitch needs the application's credentials and the registered redirect URI; without any of
    // them every attempt would fail at Twitch, so refuse to start rather than offer a button that cannot work.
    private static void verifyTwitch(GatewayEdgeProperties.Twitch twitch) {
        if (!twitch.isEnabled()) {
            return;
        }
        if (isBlank(twitch.getClientId()) || isBlank(twitch.getClientSecret())) {
            throw new IllegalStateException("streamsense.gateway.auth.twitch.enabled=true but the Twitch application "
                    + "credentials are missing; provide the TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET secrets");
        }
        String redirectUri = twitch.getRedirectUri();
        if (isBlank(redirectUri) || !(redirectUri.startsWith("https://") || redirectUri.startsWith("http://"))) {
            throw new IllegalStateException(
                    "streamsense.gateway.auth.twitch.redirect-uri must be the absolute "
                            + "callback URL registered on the Twitch application (STREAMSENSE_GATEWAY_AUTH_TWITCH_REDIRECT_URI)");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
