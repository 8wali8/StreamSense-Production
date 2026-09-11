package com.streamsense.apigateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.SignedJWT;
import com.streamsense.apigateway.support.TestJwtTokens;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Sign in with Twitch end to end against a fake Twitch, and what the minted token may then do. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events",
            "streamsense.topics.sponsorDetections=stream.sponsor.detections",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group",
            "streamsense.gateway.auth.enabled=true",
            "streamsense.gateway.auth.hmac-secret=" + TestJwtTokens.TEST_SECRET,
            "streamsense.gateway.auth.operators=OpsPerson, other-op",
            "streamsense.gateway.auth.twitch.enabled=true",
            "streamsense.gateway.auth.twitch.client-id=client-1",
            "streamsense.gateway.auth.twitch.client-secret=secret-1",
            "streamsense.gateway.auth.twitch.redirect-uri=http://console.test/auth/twitch/callback",
            "streamsense.gateway.auth.twitch.authorize-url=https://id.twitch.test/oauth2/authorize"
        })
class TwitchSignInIntegrationTest {

    private static final MockWebServer TWITCH = new MockWebServer();
    private static final Pattern STATE = Pattern.compile("[?&]state=([^&]+)");

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void twitchUrls(DynamicPropertyRegistry registry) throws IOException {
        TWITCH.start();
        registry.add("streamsense.gateway.auth.twitch.token-url", () -> TWITCH.url("/oauth2/token")
                .toString());
        registry.add("streamsense.gateway.auth.twitch.validate-url", () -> TWITCH.url("/oauth2/validate")
                .toString());
        registry.add("streamsense.gateway.auth.twitch.revoke-url", () -> TWITCH.url("/oauth2/revoke")
                .toString());
    }

    @AfterAll
    static void stopTwitch() throws IOException {
        TWITCH.shutdown();
    }

    @Test
    void advertisesTwitchSignInWithoutAToken() {
        client().get()
                .uri("/auth/providers")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.twitch")
                .isEqualTo(true);
    }

    @Test
    void loginSendsTheBrowserToTwitchWithASealedState() {
        EntityExchangeResult<Void> login = client().get()
                .uri("/auth/twitch/login?return=/deals/3")
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(Void.class)
                .returnResult();

        String location = login.getResponseHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location)
                .startsWith("https://id.twitch.test/oauth2/authorize?")
                .contains("response_type=code")
                .contains("client_id=client-1")
                .contains("redirect_uri=http://console.test/auth/twitch/callback")
                .contains("scope=openid")
                .contains("state=");
        ResponseCookie cookie = login.getResponseCookies().getFirst("streamsense_oauth");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getPath()).isEqualTo("/auth/twitch");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(600);
    }

    @Test
    void callbackWithoutAMatchingStateGoesBackToTheSignInPage() {
        client().get()
                .uri("/auth/twitch/callback?code=abc&state=forged")
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "http://console.test/?signin=state");

        Login login = login("/deals/3");
        client().get()
                .uri("/auth/twitch/callback?code=abc&state=not-" + login.state())
                .cookie("streamsense_oauth", login.cookie())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "http://console.test/?signin=state");
    }

    @Test
    void aRefusalAtTwitchGoesBackToTheSignInPageAsDenied() {
        Login login = login("/");
        client().get()
                .uri("/auth/twitch/callback?error=access_denied&state=" + login.state())
                .cookie("streamsense_oauth", login.cookie())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "http://console.test/?signin=denied");
    }

    @Test
    void aStreamerSignsInAndGetsATokenThatReadsButCannotSteerThePipeline() throws Exception {
        Login login = login("/deals/3?tab=streams");
        TWITCH.enqueue(json("{\"access_token\":\"tok-1\",\"token_type\":\"bearer\",\"expires_in\":3600}"));
        TWITCH.enqueue(json("{\"client_id\":\"client-1\",\"login\":\"StreamerOne\",\"user_id\":\"42\",\"scopes\":[]}"));
        TWITCH.enqueue(new MockResponse().setResponseCode(200));

        EntityExchangeResult<Void> callback = client().get()
                .uri("/auth/twitch/callback?code=abc&state=" + login.state())
                .cookie("streamsense_oauth", login.cookie())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody(Void.class)
                .returnResult();

        // The code was exchanged with the secret in the body, the token validated, then revoked.
        RecordedRequest exchange = TWITCH.takeRequest(5, TimeUnit.SECONDS);
        assertThat(exchange.getPath()).isEqualTo("/oauth2/token");
        Map<String, String> form = form(exchange.getBody().readUtf8());
        assertThat(form)
                .containsEntry("client_id", "client-1")
                .containsEntry("client_secret", "secret-1")
                .containsEntry("code", "abc")
                .containsEntry("grant_type", "authorization_code")
                .containsEntry("redirect_uri", "http://console.test/auth/twitch/callback");
        RecordedRequest validate = TWITCH.takeRequest(5, TimeUnit.SECONDS);
        assertThat(validate.getPath()).isEqualTo("/oauth2/validate");
        assertThat(validate.getHeader("Authorization")).isEqualTo("OAuth tok-1");
        RecordedRequest revoke = TWITCH.takeRequest(5, TimeUnit.SECONDS);
        assertThat(revoke.getPath()).isEqualTo("/oauth2/revoke");
        assertThat(form(revoke.getBody().readUtf8())).containsEntry("token", "tok-1");

        // The state cookie is cleared and the browser lands where it started, token in the fragment.
        ResponseCookie cleared = callback.getResponseCookies().getFirst("streamsense_oauth");
        assertThat(cleared).isNotNull();
        assertThat(cleared.getMaxAge().getSeconds()).isZero();
        String location = callback.getResponseHeaders().getFirst(HttpHeaders.LOCATION);
        assertThat(location).startsWith("http://console.test/deals/3?tab=streams#token=");
        String token = URI.create(location).getFragment().substring("token=".length());

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("streamerone");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("login")).isEqualTo("streamerone");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("twitchUserId")).isEqualTo("42");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("streamer");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("provider")).isEqualTo("twitch");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("streamsense-local");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("streamsense-clients");

        // The token passes the gate for reads, and is refused where it would steer the pipeline.
        client().post()
                .uri("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"{ health }\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.health")
                .isEqualTo("ok");
        client().post()
                .uri("/api/chat/twitch/channels")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"channels\":[\"streamerone\"]}")
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .contentType("application/problem+json")
                .expectBody()
                .jsonPath("$.reason")
                .isEqualTo("operator_required")
                .jsonPath("$.type")
                .isEqualTo("https://streamsense.dev/problems/forbidden");
        // A read on the same service is not the gate's business (it reaches the proxy, which has no upstream here).
        assertThat(client().get()
                        .uri("/api/chat/twitch/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus()
                        .value())
                .isNotIn(401, 403);
    }

    @Test
    void anOperatorOnTheListSignsInWithTheOperatorRoleAndAnUnsafeReturnPathIsDropped() throws Exception {
        Login login = login("https://evil.example/phish");
        TWITCH.enqueue(json("{\"access_token\":\"tok-2\"}"));
        TWITCH.enqueue(json("{\"client_id\":\"client-1\",\"login\":\"opsperson\",\"user_id\":\"7\"}"));
        TWITCH.enqueue(new MockResponse().setResponseCode(200));

        String location = client().get()
                .uri("/auth/twitch/callback?code=xyz&state=" + login.state())
                .cookie("streamsense_oauth", login.cookie())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectBody(Void.class)
                .returnResult()
                .getResponseHeaders()
                .getFirst(HttpHeaders.LOCATION);
        assertThat(location).startsWith("http://console.test/#token=");
        String token = URI.create(location).getFragment().substring("token=".length());
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("role"))
                .isEqualTo("operator");

        assertThat(client().post()
                        .uri("/api/chat/twitch/channels")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue("{\"channels\":[\"x\"]}")
                        .exchange()
                        .returnResult(Void.class)
                        .getStatus()
                        .value())
                .isNotIn(401, 403);
        for (int i = 0; i < 3; i++) {
            TWITCH.takeRequest(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void aTokenIssuedToAnotherApplicationIsRefused() throws Exception {
        Login login = login("/");
        TWITCH.enqueue(json("{\"access_token\":\"tok-3\"}"));
        TWITCH.enqueue(json("{\"client_id\":\"someone-else\",\"login\":\"mallory\",\"user_id\":\"9\"}"));
        TWITCH.enqueue(new MockResponse().setResponseCode(200));

        client().get()
                .uri("/auth/twitch/callback?code=abc&state=" + login.state())
                .cookie("streamsense_oauth", login.cookie())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "http://console.test/?signin=twitch");
        for (int i = 0; i < 3; i++) {
            TWITCH.takeRequest(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void aFailedExchangeGoesBackToTheSignInPage() throws Exception {
        Login login = login("/");
        TWITCH.enqueue(new MockResponse().setResponseCode(400).setBody("{\"message\":\"Invalid authorization code\"}"));

        client().get()
                .uri("/auth/twitch/callback?code=stale&state=" + login.state())
                .cookie("streamsense_oauth", login.cookie())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectHeader()
                .valueEquals(HttpHeaders.LOCATION, "http://console.test/?signin=twitch");
        TWITCH.takeRequest(5, TimeUnit.SECONDS);
    }

    private Login login(String returnPath) {
        EntityExchangeResult<Void> result = client().get()
                .uri(builder -> builder.path("/auth/twitch/login")
                        .queryParam("return", returnPath)
                        .build())
                .exchange()
                .expectStatus()
                .isSeeOther()
                .expectBody(Void.class)
                .returnResult();
        Matcher matcher = STATE.matcher(result.getResponseHeaders().getFirst(HttpHeaders.LOCATION));
        assertThat(matcher.find()).isTrue();
        return new Login(
                matcher.group(1),
                result.getResponseCookies().getFirst("streamsense_oauth").getValue());
    }

    private record Login(String state, String cookie) {}

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body).addHeader("Content-Type", "application/json");
    }

    private static Map<String, String> form(String body) {
        return Arrays.stream(body.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : ""));
    }

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }
}
