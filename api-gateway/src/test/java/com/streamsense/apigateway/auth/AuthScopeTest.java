package com.streamsense.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthScopeTest {

    private static final AuthScope OPERATOR = new AuthScope("ops", GatewayTokenIssuer.ROLE_OPERATOR);
    private static final AuthScope STREAMER = new AuthScope("ninja", GatewayTokenIssuer.ROLE_STREAMER);
    private static final AuthScope ACCESS_LINK = new AuthScope("demo-viewer", null);

    @Test
    void onlyAnOperatorSignInSteersThePipeline() {
        assertThat(OPERATOR.isOperator()).isTrue();
        assertThat(STREAMER.isOperator()).isFalse();
        assertThat(ACCESS_LINK.isOperator()).isFalse();
    }

    @Test
    void aStreamerIsConfinedToTheirOwnChannelAndNobodyElseIs() {
        assertThat(STREAMER.isConfined()).isTrue();
        assertThat(STREAMER.allows("ninja")).isTrue();
        assertThat(STREAMER.allows("@Ninja ")).isTrue();
        assertThat(STREAMER.allows("pokimane")).isFalse();
        assertThat(STREAMER.allows(null)).isTrue();

        assertThat(OPERATOR.isConfined()).isFalse();
        assertThat(OPERATOR.allows("pokimane")).isTrue();
        // An access link reads any channel; its subject is not a channel and must not be compared as one.
        assertThat(ACCESS_LINK.isConfined()).isFalse();
        assertThat(ACCESS_LINK.allows("pokimane")).isTrue();
    }

    @Test
    void findsTheChannelARestRequestIsAbout() {
        assertThat(AuthScope.restChannel("/api/analytics/streams/ninja/sessions", null))
                .isEqualTo("ninja");
        assertThat(AuthScope.restChannel("/api/sentiment/relevance/sponsors/ninja", null))
                .isEqualTo("ninja");
        assertThat(AuthScope.restChannel("/api/analytics/deals", "ninja")).isEqualTo("ninja");
        assertThat(AuthScope.restChannel("/api/analytics/deals", " ")).isNull();
        assertThat(AuthScope.restChannel("/api/analytics/deals/3", null)).isNull();
    }
}
