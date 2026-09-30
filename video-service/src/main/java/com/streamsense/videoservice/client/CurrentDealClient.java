package com.streamsense.videoservice.client;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Asks analytics-service which deal a channel is in right now and what logos it carries, so a frame
 * is examined against the right logo, or not examined at all when there is none. Answers are kept
 * for {@code streamsense.services.analytics-service.current-deal-cache-seconds} per channel (a
 * frame arrives every ten seconds; a new logo takes effect within the cache's life), including "no
 * deal". A failure is an {@link AnalyticsDependencyException}: the caller reports the frame as
 * unavailable rather than guessing.
 */
public class CurrentDealClient {

    private static final Logger log = LoggerFactory.getLogger(CurrentDealClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final long cacheMs;
    private final Clock clock;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public CurrentDealClient(RestTemplate restTemplate, String baseUrl, long cacheMs, Clock clock) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.cacheMs = cacheMs;
        this.clock = clock;
    }

    /** The channel's current deal, or empty when it has none. */
    public Optional<CurrentDeal> find(String streamer) {
        String login = normalize(streamer);
        long now = clock.millis();
        Cached cached = cache.get(login);
        if (cached != null && cached.expiresAt() > now) {
            return cached.deal();
        }
        Optional<CurrentDeal> deal = fetch(login);
        cache.put(login, new Cached(deal, now + cacheMs));
        return deal;
    }

    /** Forgets every cached answer; the next frame asks again. */
    public void invalidate() {
        cache.clear();
    }

    private Optional<CurrentDeal> fetch(String login) {
        try {
            ResponseEntity<CurrentDeal> response = restTemplate.getForEntity(
                    baseUrl + "/api/analytics/streams/{streamer}/current-deal", CurrentDeal.class, login);
            CurrentDeal deal = response.getBody();
            log.info(
                    "current deal for @{}: {}",
                    login,
                    deal == null
                            ? "none"
                            : "deal " + deal.id() + " (" + deal.sponsor() + ", "
                                    + deal.logos().size() + " logos)");
            return Optional.ofNullable(deal);
        } catch (HttpClientErrorException.NotFound notFound) {
            log.info("current deal for @{}: none", login);
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new AnalyticsDependencyException("current deal lookup failed for @" + login, ex);
        }
    }

    private static String normalize(String streamer) {
        return streamer == null
                ? ""
                : streamer.trim().replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
    }

    private record Cached(Optional<CurrentDeal> deal, long expiresAt) {}
}
