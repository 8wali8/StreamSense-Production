package com.streamsense.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthScopeTest {

    private static final AuthScope OPERATOR = new AuthScope("ops", GatewayTokenIssuer.ROLE_OPERATOR);
    private static final AuthScope STREAMER = new AuthScope("ninja", GatewayTokenIssuer.ROLE_STREAMER);

    @Test
    void onlyAnOperatorSignInSteersThePipeline() {
        assertThat(OPERATOR.isOperator()).isTrue();
        assertThat(STREAMER.isOperator()).isFalse();
        // The validator never lets a token without a role through; should one appear, it is nobody's operator.
        assertThat(new AuthScope("stray", null).isOperator()).isFalse();
    }

    @Test
    void aStreamerIsConfinedToTheirOwnChannelAndAnOperatorIsNot() {
        assertThat(STREAMER.allows("ninja")).isTrue();
        assertThat(STREAMER.allows("@Ninja ")).isTrue();
        assertThat(STREAMER.allows("pokimane")).isFalse();
        assertThat(STREAMER.allows(null)).isTrue();

        assertThat(OPERATOR.allows("pokimane")).isTrue();
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
