package com.streamsense.analyticsservice.config;

import com.streamsense.analyticsservice.service.DealService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.StreamSessionPoller;
import com.streamsense.analyticsservice.twitch.TwitchAppTokenProvider;
import com.streamsense.analyticsservice.twitch.TwitchHelixClient;
import java.time.Clock;
import java.time.Duration;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Scheduling for the session jobs, and the Helix poller when it is enabled and credentials are
 * present. Every outbound call to Twitch is bounded by the connect and read timeouts, and the HTTP
 * client's own retries are off: {@link TwitchHelixClient} retries a dropped connection once itself
 * and pauses on 429, whereas Apache's default would resend a 429 after a second and spend budget.
 */
@Configuration
@EnableScheduling
public class TwitchHelixConfig {

    @Bean
    public RestClientCustomizer twitchTimeoutRestClientCustomizer(StreamSenseProperties properties) {
        ClientHttpRequestFactory factory =
                twitchRequestFactory(properties.getTwitch().getHelix());
        return builder -> builder.requestFactory(factory);
    }

    /** The request factory every Twitch call goes through: bounded timeouts, no automatic retries. */
    public static ClientHttpRequestFactory twitchRequestFactory(StreamSenseProperties.Helix helix) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(helix.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(helix.getReadTimeoutMs()));
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(HttpClientBuilder::disableAutomaticRetries)
                .build(settings);
    }

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.twitch.helix", name = "enabled", havingValue = "true")
    public TwitchAppTokenProvider twitchAppTokenProvider(RestClient.Builder builder, StreamSenseProperties properties) {
        StreamSenseProperties.Helix helix = properties.getTwitch().getHelix();
        if (helix.getClientId() == null
                || helix.getClientId().isBlank()
                || helix.getClientSecret() == null
                || helix.getClientSecret().isBlank()) {
            throw new IllegalStateException(
                    "streamsense.twitch.helix.enabled=true needs TWITCH_CLIENT_ID and TWITCH_CLIENT_SECRET");
        }
        return new TwitchAppTokenProvider(builder, helix, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.twitch.helix", name = "enabled", havingValue = "true")
    public TwitchHelixClient twitchHelixClient(
            RestClient.Builder builder, TwitchAppTokenProvider tokens, StreamSenseProperties properties) {
        return new TwitchHelixClient(builder, tokens, properties.getTwitch().getHelix());
    }

    @Bean
    @ConditionalOnProperty(prefix = "streamsense.twitch.helix", name = "enabled", havingValue = "true")
    public StreamSessionPoller streamSessionPoller(
            TwitchHelixClient helixClient,
            StreamSessionService sessions,
            DealService deals,
            StreamSenseProperties properties) {
        return new StreamSessionPoller(
                helixClient,
                sessions,
                deals::streamersWithActiveDeals,
                properties.getTwitch().getHelix(),
                Clock.systemUTC());
    }
}
