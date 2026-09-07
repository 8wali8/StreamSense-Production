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
