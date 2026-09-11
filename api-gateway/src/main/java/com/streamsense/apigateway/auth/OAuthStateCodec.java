package com.streamsense.apigateway.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The OAuth {@code state} and where to send the browser afterwards, sealed into one cookie value so the
 * callback can check them without server-side session state (any gateway replica can finish a sign-in
 * another one started). The value is {@code base64url(json) + "." + base64url(hmac-sha256)} under the
 * gateway's own HS256 secret; a tampered or expired value decodes to empty.
 */
@Component
public class OAuthStateCodec {

    public static final String COOKIE_NAME = "streamsense_oauth";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GatewayEdgeProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public OAuthStateCodec(GatewayEdgeProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    OAuthStateCodec(GatewayEdgeProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** A fresh state for one sign-in attempt, valid for the configured state lifetime. */
    public OAuthState begin(String returnPath) {
        byte[] nonce = new byte[24];
        RANDOM.nextBytes(nonce);
        Duration ttl = properties.getAuth().getTwitch().getStateTtl();
        return new OAuthState(
                ENCODER.encodeToString(nonce),
                safeReturnPath(returnPath),
                clock.instant().plus(ttl).getEpochSecond());
    }

    public String encode(OAuthState state) {
        try {
            String body = ENCODER.encodeToString(objectMapper.writeValueAsBytes(state));
            return body + "." + ENCODER.encodeToString(sign(body));
        } catch (Exception exception) {
            throw new IllegalStateException("could not seal the OAuth state", exception);
        }
    }

    /** The state sealed in a cookie value, if its signature holds and it has not expired. */
    public Optional<OAuthState> decode(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            return Optional.empty();
        }
        int dot = cookieValue.indexOf('.');
        if (dot <= 0 || dot == cookieValue.length() - 1) {
            return Optional.empty();
        }
        String body = cookieValue.substring(0, dot);
        try {
            byte[] expected = sign(body);
            byte[] actual = DECODER.decode(cookieValue.substring(dot + 1));
            if (!MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            OAuthState state = objectMapper.readValue(DECODER.decode(body), OAuthState.class);
            if (state.nonce() == null || state.expiresAt() <= clock.instant().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(state);
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    /**
     * Where the console goes after sign-in: an origin-relative path only. Anything that could leave the
     * console (an absolute URL, a protocol-relative {@code //host}, a backslash trick) falls back to the root,
     * so the redirect after sign-in can never be aimed at another site.
     */
    public static String safeReturnPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "/";
        }
        String path = candidate.trim();
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.contains("://")) {
            return "/";
        }
        // The fragment is where the token goes; a fragment in the return path would collide with it.
        int hash = path.indexOf('#');
        return hash >= 0 ? path.substring(0, hash) : path;
    }

    private byte[] sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(properties.getAuth().getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
    }

    /** One sign-in attempt: the {@code state} sent to Twitch, where to return, and when this stops being valid. */
    public record OAuthState(String nonce, String returnPath, long expiresAt) {}
}
