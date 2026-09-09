package com.streamsense.analyticsservice.relevance;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Points sentiment-service's relevance scoring at a sponsor for a channel, so a deal created here
 * is what chat and transcript lines are matched against. Best effort: a failure is logged and the
 * deal still exists; the operations page can re-point the channel by hand.
 */
public class SponsorRelevancePointer {

    private static final Logger log = LoggerFactory.getLogger(SponsorRelevancePointer.class);

    private final RestClient restClient;

    public SponsorRelevancePointer(RestClient.Builder builder, StreamSenseProperties.SentimentService sentiment) {
        this.restClient = builder.baseUrl(sentiment.getBaseUrl()).build();
    }

    /** True when the profile was accepted. */
    public boolean point(String streamer, String sponsor) {
        try {
            restClient
                    .post()
                    .uri("/api/sentiment/relevance/sponsors")
                    .body(Map.of("streamer", streamer, "sponsor", sponsor))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException ex) {
            log.warn("could not point relevance at {} for @{}: {}", sponsor, streamer, ex.getMessage());
            return false;
        }
    }
}
