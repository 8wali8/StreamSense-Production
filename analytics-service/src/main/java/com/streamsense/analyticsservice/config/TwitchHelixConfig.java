package com.streamsense.analyticsservice.config;

import com.streamsense.analyticsservice.service.DealService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.StreamSessionPoller;
import com.streamsense.analyticsservice.twitch.TwitchAppTokenProvider;
import com.streamsense.analyticsservice.twitch.TwitchHelixClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

/**
 * Scheduling for the session jobs, and the Helix poller when it is enabled and credentials are
 * present. Every outbound call to Twitch is bounded by the connect and read timeouts.
 */
@Configuration
@EnableScheduling
public class TwitchHelixConfig {

    @Bean
    public RestClientCustomizer twitchTimeoutRestClientCustomizer(StreamSenseProperties properties) {
        StreamSenseProperties.Helix helix = properties.getTwitch().getHelix();
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(helix.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(helix.getReadTimeoutMs()));
        return builder ->
                builder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
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
