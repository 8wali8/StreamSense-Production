package com.streamsense.apigateway.auth;

import com.streamsense.apigateway.config.GatewayEdgeProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Sign in with Twitch, start to finish. {@link #begin} sends the browser to Twitch with a sealed state
 * cookie; {@link #complete} turns the callback into the gateway's own token and the address to send
 * the browser to. The token travels in the URL fragment, which browsers never send to a server, so the
 * console picks it up exactly as it does an access link and nothing else in the gateway changes.
 */
@Component
public class TwitchSignInService {

    private static final Logger log = LoggerFactory.getLogger(TwitchSignInService.class);

    private final GatewayEdgeProperties properties;
    private final OAuthStateCodec stateCodec;
    private final TwitchIdentityClient twitch;
    private final GatewayTokenIssuer tokenIssuer;

    public TwitchSignInService(
            GatewayEdgeProperties properties,
            OAuthStateCodec stateCodec,
            TwitchIdentityClient twitch,
            GatewayTokenIssuer tokenIssuer) {
        this.properties = properties;
        this.stateCodec = stateCodec;
        this.twitch = twitch;
        this.tokenIssuer = tokenIssuer;
    }

    public boolean isEnabled() {
        return properties.getAuth().isEnabled()
                && properties.getAuth().getTwitch().isEnabled();
    }

    /** Where to send the browser, and the cookie that lets the callback trust what comes back. */
    public Begin begin(String returnPath) {
        GatewayEdgeProperties.Twitch twitch = properties.getAuth().getTwitch();
        OAuthStateCodec.OAuthState state = stateCodec.begin(returnPath);
        URI authorize = UriComponentsBuilder.fromUriString(twitch.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", twitch.getClientId())
                .queryParam("redirect_uri", twitch.getRedirectUri())
                // Twitch requires a scope; openid is the one that asks for nothing beyond who the person is.
                .queryParam("scope", "openid")
                .queryParam("state", state.nonce())
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        return new Begin(
                authorize,
                stateCookie(stateCodec.encode(state), twitch.getStateTtl().toSeconds()));
    }

    /**
     * The address to send the browser to after the callback: the console with the token in the fragment on
     * success, the sign-in page with a reason otherwise. Never errors; every failure is a redirect.
     */
    public Mono<Outcome> complete(String code, String stateParam, String cookieValue, String twitchError) {
        if (twitchError != null && !twitchError.isBlank()) {
            return Mono.just(Outcome.failure("access_denied".equals(twitchError) ? "denied" : "twitch"));
        }
        Optional<OAuthStateCodec.OAuthState> state = stateCodec.decode(cookieValue);
        if (state.isEmpty()
                || stateParam == null
                || !stateParam.equals(state.get().nonce())) {
            return Mono.just(Outcome.failure("state"));
        }
        if (code == null || code.isBlank()) {
            return Mono.just(Outcome.failure("twitch"));
        }
        String returnPath = state.get().returnPath();
        return twitch.exchangeCode(code)
                .flatMap(accessToken -> twitch.validate(accessToken)
                        .flatMap(identity -> twitch.revoke(accessToken).thenReturn(identity)))
                .map(identity -> signIn(identity, returnPath))
                .onErrorResume(exception -> {
                    log.warn("Twitch sign-in failed: {}", exception.getMessage());
                    return Mono.just(Outcome.failure("twitch"));
                });
    }

    private Outcome signIn(TwitchIdentityClient.TwitchIdentity identity, String returnPath) {
        GatewayEdgeProperties.Twitch twitch = properties.getAuth().getTwitch();
        if (identity.login() == null || identity.login().isBlank()) {
            return Outcome.failure("twitch");
        }
        // A token validated against another application's client id is not one we asked Twitch for.
        if (identity.clientId() != null && !identity.clientId().equals(twitch.getClientId())) {
            log.warn("Twitch token belongs to another application; refusing sign-in");
            return Outcome.failure("twitch");
        }
        String login = identity.login().toLowerCase();
        String token = tokenIssuer.issue(
                new GatewayTokenIssuer.SignedInUser(login, identity.userId(), tokenIssuer.roleFor(login)));
        log.info("Twitch sign-in login={} role={}", login, tokenIssuer.roleFor(login));
        return Outcome.success(URI.create(consoleOrigin() + returnPath + "#token=" + token));
    }

    /** The sign-in page with the reason the attempt failed; the console turns it into a sentence. */
    public URI failureAddress(String reason) {
        return URI.create(consoleOrigin() + "/?signin=" + reason);
    }

    /** A cookie that removes the state cookie once the callback has used it. */
    public ResponseCookie clearedStateCookie() {
        return stateCookie("", 0);
    }

    private ResponseCookie stateCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(OAuthStateCodec.COOKIE_NAME, value)
                .httpOnly(true)
                .secure("https".equalsIgnoreCase(URI.create(redirectUri()).getScheme()))
                .sameSite("Lax")
                .path("/auth/twitch")
                .maxAge(maxAgeSeconds)
                .build();
    }

    /** The console lives where the redirect URI lives; the callback route is on the same origin as the console. */
    private String consoleOrigin() {
        URI uri = URI.create(redirectUri());
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private String redirectUri() {
        return properties.getAuth().getTwitch().getRedirectUri();
    }

    public record Begin(URI authorizeUrl, ResponseCookie stateCookie) {}

    public record Outcome(URI redirectTo, String failureReason) {

        static Outcome success(URI redirectTo) {
            return new Outcome(redirectTo, null);
        }

        static Outcome failure(String reason) {
            return new Outcome(null, reason);
        }

        public boolean succeeded() {
            return failureReason == null;
        }
    }
}
