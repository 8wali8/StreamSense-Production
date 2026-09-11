package com.streamsense.apigateway.web;

import com.streamsense.apigateway.auth.OAuthStateCodec;
import com.streamsense.apigateway.auth.TwitchSignInService;
import java.util.Map;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The sign-in routes the console's nginx forwards under {@code /auth/}. Nothing here needs a bearer
 * token: these are how a browser gets one. Every answer is a redirect or a tiny JSON, so the pages
 * themselves stay in the console.
 */
@RestController
@RequestMapping("/auth")
public class TwitchSignInController {

    private final TwitchSignInService signIn;

    public TwitchSignInController(TwitchSignInService signIn) {
        this.signIn = signIn;
    }

    /** Which sign-in methods this deployment offers, so the sign-in page shows only real buttons. */
    @GetMapping("/providers")
    public Map<String, Boolean> providers() {
        return Map.of("twitch", signIn.isEnabled());
    }

    @GetMapping("/twitch/login")
    public ResponseEntity<Void> login(@RequestParam(name = "return", required = false) String returnPath) {
        if (!signIn.isEnabled()) {
            return redirect(signIn.failureAddress("disabled").toString(), null);
        }
        TwitchSignInService.Begin begin = signIn.begin(returnPath);
        return redirect(begin.authorizeUrl().toString(), begin.stateCookie().toString());
    }

    @GetMapping("/twitch/callback")
    public Mono<ResponseEntity<Void>> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            ServerHttpRequest request) {
        if (!signIn.isEnabled()) {
            return Mono.just(redirect(signIn.failureAddress("disabled").toString(), null));
        }
        HttpCookie cookie = request.getCookies().getFirst(OAuthStateCodec.COOKIE_NAME);
        String cleared = signIn.clearedStateCookie().toString();
        return signIn.complete(code, state, cookie == null ? null : cookie.getValue(), error)
                .map(outcome -> redirect(
                        outcome.succeeded()
                                ? outcome.redirectTo().toString()
                                : signIn.failureAddress(outcome.failureReason()).toString(),
                        cleared));
    }

    private static ResponseEntity<Void> redirect(String location, String setCookie) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, location)
                // A redirect that carries a token must never be cached or reused.
                .header(HttpHeaders.CACHE_CONTROL, "no-store");
        if (setCookie != null) {
            response.header(HttpHeaders.SET_COOKIE, setCookie);
        }
        return response.build();
    }
}
