package com.streamsense.apigateway.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.streamsense.apigateway.config.GatewayEdgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * The three calls to Twitch's OAuth server a sign-in needs: exchange the code for a user token, ask
 * who that token belongs to, and revoke it again because the gateway keeps nothing of Twitch's. The
 * client secret only ever travels in a form body. Timeouts come from the shared WebClient customizer.
 */
@Component
public class TwitchIdentityClient {

    private static final Logger log = LoggerFactory.getLogger(TwitchIdentityClient.class);

    private final WebClient webClient;
    private final GatewayEdgeProperties properties;

    public TwitchIdentityClient(WebClient.Builder webClientBuilder, GatewayEdgeProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    /** The user access token for an authorization code. */
    public Mono<String> exchangeCode(String code) {
        GatewayEdgeProperties.Twitch twitch = properties.getAuth().getTwitch();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", twitch.getClientId());
        form.add("client_secret", twitch.getClientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", twitch.getRedirectUri());
        return webClient
                .post()
                .uri(twitch.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .flatMap(response ->
                        response.accessToken() == null || response.accessToken().isBlank()
                                ? Mono.error(new IllegalStateException("Twitch returned no access token"))
                                : Mono.just(response.accessToken()));
    }

    /** Who a user token belongs to, and which application it was issued to. */
    public Mono<TwitchIdentity> validate(String accessToken) {
        return webClient
                .get()
                .uri(properties.getAuth().getTwitch().getValidateUrl())
                .header("Authorization", "OAuth " + accessToken)
                .retrieve()
                .bodyToMono(TwitchIdentity.class);
    }

    /** Best effort: a failure here is logged, never surfaced, because the sign-in itself has succeeded. */
    public Mono<Void> revoke(String accessToken) {
        GatewayEdgeProperties.Twitch twitch = properties.getAuth().getTwitch();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", twitch.getClientId());
        form.add("token", accessToken);
        return webClient
                .post()
                .uri(twitch.getRevokeUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .toBodilessEntity()
                .then()
                .onErrorResume(exception -> {
                    log.debug("Twitch token revoke failed: {}", exception.getMessage());
                    return Mono.empty();
                });
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TwitchIdentity(
            @JsonProperty("login") String login,
            @JsonProperty("user_id") String userId,
            @JsonProperty("client_id") String clientId) {}
}
