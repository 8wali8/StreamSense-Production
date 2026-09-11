package com.streamsense.apigateway.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.streamsense.apigateway.support.TestJwtTokens;
import org.junit.jupiter.api.Test;

class GatewayAuthStartupCheckTest {

    @Test
    void ignoresKeyConfigurationWhileAuthIsDisabled() {
        GatewayEdgeProperties properties = new GatewayEdgeProperties();
        properties.getAuth().setEnabled(false);

        assertThatCode(() -> new GatewayAuthStartupCheck(properties).verify()).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWithAuthEnabledAndNoKey() {
        GatewayEdgeProperties properties = new GatewayEdgeProperties();
        properties.getAuth().setEnabled(true);

        assertThatThrownBy(() -> new GatewayAuthStartupCheck(properties).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET");
    }

    @Test
    void refusesTwitchSignInWithoutTheGateOrTheApplication() {
        GatewayEdgeProperties properties = new GatewayEdgeProperties();
        properties.getAuth().setEnabled(false);
        properties.getAuth().getTwitch().setEnabled(true);
        assertThatThrownBy(() -> new GatewayAuthStartupCheck(properties).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("streamsense.gateway.auth.enabled=true");

        properties.getAuth().setEnabled(true);
        properties.getAuth().setHmacSecret(TestJwtTokens.TEST_SECRET);
        assertThatThrownBy(() -> new GatewayAuthStartupCheck(properties).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TWITCH_CLIENT_ID");

        properties.getAuth().getTwitch().setClientId("client");
        properties.getAuth().getTwitch().setClientSecret("secret");
        properties.getAuth().getTwitch().setRedirectUri("console/callback");
        assertThatThrownBy(() -> new GatewayAuthStartupCheck(properties).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirect-uri");

        properties.getAuth().getTwitch().setRedirectUri("https://streamsense.dev/auth/twitch/callback");
        assertThatCode(() -> new GatewayAuthStartupCheck(properties).verify()).doesNotThrowAnyException();
    }

    @Test
    void refusesShortKeys() {
        GatewayEdgeProperties properties = new GatewayEdgeProperties();
        properties.getAuth().setEnabled(true);
        properties.getAuth().setHmacSecret("too-short");

        assertThatThrownBy(() -> new GatewayAuthStartupCheck(properties).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void acceptsAUsableKey() {
        GatewayEdgeProperties properties = new GatewayEdgeProperties();
        properties.getAuth().setEnabled(true);
        properties.getAuth().setHmacSecret(TestJwtTokens.TEST_SECRET);

        assertThatCode(() -> new GatewayAuthStartupCheck(properties).verify()).doesNotThrowAnyException();
    }
}
