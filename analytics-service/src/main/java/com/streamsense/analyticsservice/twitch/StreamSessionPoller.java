package com.streamsense.analyticsservice.twitch;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.service.StreamSessionService;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Every poll: ask Helix which watched channels are live, record a viewer sample for each, and
 * close the Helix sessions of watched channels that are no longer live. Watched channels are the
 * configured list, every streamer that produced an event recently or has a running deal, and every
 * channel with a Helix session still open.
 */
public class StreamSessionPoller {

    private static final Logger log = LoggerFactory.getLogger(StreamSessionPoller.class);

    private final TwitchHelixClient helixClient;
    private final StreamSessionService sessions;
    private final Supplier<List<String>> dealStreamers;
    private final StreamSenseProperties.Helix helix;
    private final Clock clock;

    public StreamSessionPoller(
            TwitchHelixClient helixClient,
            StreamSessionService sessions,
            Supplier<List<String>> dealStreamers,
            StreamSenseProperties.Helix helix,
            Clock clock) {
        this.helixClient = helixClient;
        this.sessions = sessions;
        this.dealStreamers = dealStreamers;
        this.helix = helix;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${streamsense.twitch.helix.poll-interval-ms:60000}",
            initialDelayString = "${streamsense.twitch.helix.initial-delay-ms:15000}")
    public void poll() {
        try {
            Set<String> watched = watchedChannels();
            if (watched.isEmpty()) {
                return;
            }
            List<HelixStream> live = helixClient.liveStreams(watched);
            for (HelixStream stream : live) {
                sessions.recordHelixLive(stream);
            }
            int closed = sessions.closeHelixSessionsNotLive(
                    watched, live.stream().map(HelixStream::id).toList());
            log.debug("helix poll watched={} live={} closed={}", watched.size(), live.size(), closed);
        } catch (RuntimeException ex) {
            // The next poll retries; a Twitch outage must not stop the scheduler.
            log.warn("helix poll failed: {}", ex.getMessage());
        }
    }

    Set<String> watchedChannels() {
        Set<String> watched = new LinkedHashSet<>();
        for (String channel : helix.getChannels()) {
            if (channel != null && !channel.isBlank()) {
                watched.add(channel.trim().toLowerCase(Locale.ROOT));
            }
        }
        long since = clock.millis() - helix.getWatchStreamersSeenWithinHours() * 3_600_000L;
        for (String streamer : sessions.streamersSeenSince(since)) {
            watched.add(streamer.toLowerCase(Locale.ROOT));
        }
        for (String streamer : dealStreamers.get()) {
            watched.add(streamer.toLowerCase(Locale.ROOT));
        }
        // A channel taken off the list, or whose events dried up, is still polled until its open session
        // ends; otherwise that session would stay open forever (seen on the VM on 2026-09-11).
        for (String streamer : sessions.streamersWithOpenHelixSessions()) {
            watched.add(streamer.toLowerCase(Locale.ROOT));
        }
        return watched;
    }
}
