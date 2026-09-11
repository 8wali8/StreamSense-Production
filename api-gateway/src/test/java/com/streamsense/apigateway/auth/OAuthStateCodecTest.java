package com.streamsense.apigateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import com.streamsense.apigateway.support.TestJwtTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OAuthStateCodecTest {

    private final GatewayEdgeProperties properties = new GatewayEdgeProperties();
    private final Instant now = Instant.parse("2026-09-11T12:00:00Z");

    OAuthStateCodecTest() {
        properties.getAuth().setHmacSecret(TestJwtTokens.TEST_SECRET);
    }

    private OAuthStateCodec codecAt(Instant instant) {
        return new OAuthStateCodec(properties, new ObjectMapper(), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void sealsTheStateAndReturnPathAndReadsThemBack() {
        OAuthStateCodec codec = codecAt(now);
        OAuthStateCodec.OAuthState state = codec.begin("/deals/3?tab=streams");
        assertThat(state.nonce()).hasSizeGreaterThan(20);
        assertThat(state.returnPath()).isEqualTo("/deals/3?tab=streams");
        assertThat(state.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)).getEpochSecond());

        assertThat(codec.decode(codec.encode(state))).contains(state);
    }

    @Test
    void rejectsTamperedExpiredAndMalformedValues() {
        OAuthStateCodec codec = codecAt(now);
        String sealed = codec.encode(codec.begin("/"));
        String body = sealed.substring(0, sealed.indexOf('.'));
        String signature = sealed.substring(sealed.indexOf('.') + 1);

        assertThat(codec.decode(body + "." + signature.substring(1) + "A")).isEmpty();
        assertThat(codec.decode("x" + body.substring(1) + "." + signature)).isEmpty();
        assertThat(codec.decode(body)).isEmpty();
        assertThat(codec.decode("")).isEmpty();
        assertThat(codec.decode(null)).isEmpty();
        assertThat(codecAt(now.plus(Duration.ofMinutes(11))).decode(sealed)).isEmpty();

        // A different secret is a different signer.
        GatewayEdgeProperties other = new GatewayEdgeProperties();
        other.getAuth().setHmacSecret("another-secret-that-is-also-at-least-32-bytes");
        assertThat(new OAuthStateCodec(other, new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC)).decode(sealed))
                .isEmpty();
    }

    @Test
    void keepsTheReturnPathInsideTheConsole() {
        assertThat(OAuthStateCodec.safeReturnPath("/deals/3")).isEqualTo("/deals/3");
        assertThat(OAuthStateCodec.safeReturnPath("/sessions/9?x=1#old")).isEqualTo("/sessions/9?x=1");
        assertThat(OAuthStateCodec.safeReturnPath(null)).isEqualTo("/");
        assertThat(OAuthStateCodec.safeReturnPath("  ")).isEqualTo("/");
        assertThat(OAuthStateCodec.safeReturnPath("https://evil.example/")).isEqualTo("/");
        assertThat(OAuthStateCodec.safeReturnPath("//evil.example/")).isEqualTo("/");
        assertThat(OAuthStateCodec.safeReturnPath("/\\evil.example")).isEqualTo("/");
        assertThat(OAuthStateCodec.safeReturnPath("deals/3")).isEqualTo("/");
    }
}
