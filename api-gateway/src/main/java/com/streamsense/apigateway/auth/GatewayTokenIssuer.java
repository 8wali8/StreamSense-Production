package com.streamsense.apigateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Mints the gateway's own bearer token for someone who signed in with Twitch: the same HS256 secret,
 * issuer, and audience {@link JwtAuthTokenValidator} checks, so the rest of the gateway does not care
 * whether a token came from here or from {@code tools/mint-jwt.py}. The extra claims say who it is.
 */
@Component
public class GatewayTokenIssuer {

    public static final String ROLE_OPERATOR = "operator";
    public static final String ROLE_STREAMER = "streamer";
    public static final String CLAIM_LOGIN = "login";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TWITCH_USER_ID = "twitchUserId";
    public static final String CLAIM_PROVIDER = "provider";

    private final GatewayEdgeProperties properties;
    private final Clock clock;

    @Autowired
    public GatewayTokenIssuer(GatewayEdgeProperties properties) {
        this(properties, Clock.systemUTC());
    }

    GatewayTokenIssuer(GatewayEdgeProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String issue(SignedInUser user) {
        GatewayEdgeProperties.Auth auth = properties.getAuth();
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(user.login())
                .issuer(auth.getRequiredIssuer())
                .audience(List.of(auth.getRequiredAudience()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(auth.getTwitch().getTokenTtl())))
                .claim(CLAIM_LOGIN, user.login())
                .claim(CLAIM_TWITCH_USER_ID, user.twitchUserId())
                .claim(CLAIM_ROLE, user.role())
                .claim(CLAIM_PROVIDER, "twitch")
                .build();
        try {
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.HS256)
                            .type(JOSEObjectType.JWT)
                            .build(),
                    claims);
            jwt.sign(new MACSigner(auth.getHmacSecret().getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("could not sign the console token", exception);
        }
    }

    /** The role a Twitch login gets: operator when on the configured list, streamer otherwise. */
    public String roleFor(String login) {
        return properties.getAuth().isOperator(login) ? ROLE_OPERATOR : ROLE_STREAMER;
    }

    public record SignedInUser(String login, String twitchUserId, String role) {}
}
