package com.streamsense.analyticsservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.service.StreamSessionService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StreamSessionPollerTest {

    private final TwitchHelixClient helix = mock(TwitchHelixClient.class);
    private final StreamSessionService sessions = mock(StreamSessionService.class);
    private final StreamSenseProperties.Helix config = new StreamSenseProperties.Helix();
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneOffset.UTC);

    @Test
    void pollsConfiguredAndRecentChannelsRecordsLiveOnesAndClosesTheRest() {
        config.setChannels(List.of(" Racer ", "other"));
        config.setWatchStreamersSeenWithinHours(24);
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of("racer", "redbull-testing"));
        HelixStream live = new HelixStream("41", "racer", "Monza night", "Formula 1", 1200, 1_799_999_000_000L);
        when(helix.liveStreams(any())).thenReturn(List.of(live));
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, clock);

        assertThat(poller.watchedChannels()).isEqualTo(Set.of("racer", "other", "redbull-testing"));
        poller.poll();

        verify(sessions, atLeastOnce()).streamersSeenSince(1_800_000_000_000L - 24 * 3_600_000L);
        verify(sessions).recordHelixLive(live);
        verify(sessions).closeHelixSessionsNotLive(Set.of("racer", "other", "redbull-testing"), List.of("41"));
        // The operations page reads what the poll did, not who was watched.
        assertThat(poller.status().enabled()).isTrue();
        assertThat(poller.status().lastPollAt()).isEqualTo(clock.millis());
        assertThat(poller.status().watched()).isEqualTo(3);
        assertThat(poller.status().live()).isEqualTo(1);
        assertThat(poller.status().lastError()).isNull();
        assertThat(poller.status().pausedUntil()).isNull();
    }

    @Test
    void aSlowTwitchStampsTheOutcomeWhenItIsKnownNotWhenTheAttemptBegan() {
        config.setChannels(List.of("racer"));
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        // The clock advances on every read: the attempt, the watch list, then the answer.
        long[] ticks = {1_800_000_000_000L, 1_800_000_045_000L, 1_800_000_060_000L, 1_800_000_099_000L};
        int[] tick = {0};
        Clock stepping = mock(Clock.class);
        when(stepping.millis()).thenAnswer(invocation -> ticks[Math.min(tick[0]++, ticks.length - 1)]);
        when(helix.liveStreams(any())).thenReturn(List.of());
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, stepping);

        poller.poll();

        assertThat(poller.status().lastAttemptAt()).isEqualTo(1_800_000_000_000L);
        assertThat(poller.status().lastPollAt()).isGreaterThan(1_800_000_000_000L);
    }

    @Test
    void nothingHasRunUntilTheFirstPoll() {
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, clock);

        assertThat(poller.status().enabled()).isTrue();
        assertThat(poller.status().lastAttemptAt()).isNull();
        assertThat(poller.status().pollIntervalMs()).isEqualTo(config.getPollIntervalMs());
    }

    @Test
    void aChannelWithAnOpenSessionStaysPolledUntilItEndsEvenWhenNoLongerWatched() {
        config.setChannels(List.of("racer"));
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        when(sessions.streamersWithOpenHelixSessions()).thenReturn(List.of("Formerly-Watched"));
        when(helix.liveStreams(any())).thenReturn(List.of());
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, clock);

        assertThat(poller.watchedChannels()).isEqualTo(Set.of("racer", "formerly-watched"));
        poller.poll();

        verify(helix).liveStreams(Set.of("racer", "formerly-watched"));
        verify(sessions).closeHelixSessionsNotLive(Set.of("racer", "formerly-watched"), List.of());
    }

    @Test
    void aTwitchFailureIsLoggedAndTheNextPollStillRuns() {
        config.setChannels(List.of("racer"));
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        when(helix.liveStreams(any())).thenThrow(new IllegalStateException("twitch down"));
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, clock);

        poller.poll();

        verify(sessions, never()).closeHelixSessionsNotLive(any(), any());
        assertThat(poller.status().lastError()).isEqualTo("twitch down");
        assertThat(poller.status().lastErrorAt()).isEqualTo(clock.millis());
        assertThat(poller.status().lastAttemptAt()).isEqualTo(clock.millis());
        assertThat(poller.status().lastPollAt()).isNull();
    }

    @Test
    void aRateLimitedClientMeansNoSessionIsClosedAndTheNextPollStillRuns() {
        config.setChannels(List.of("racer"));
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        when(helix.liveStreams(any())).thenThrow(new HelixRateLimitedException(clock.millis() + 30_000L));
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, clock);

        poller.poll();
        poller.poll();

        verify(sessions, never()).recordHelixLive(any());
        verify(sessions, never()).closeHelixSessionsNotLive(any(), any());
        assertThat(poller.status().pausedUntil()).isEqualTo(clock.millis() + 30_000L);
        assertThat(poller.status().lastError()).isNull();
    }

    @Test
    void nothingToWatchMeansNoCall() {
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        new StreamSessionPoller(helix, sessions, List::of, config, clock).poll();
        verify(helix, never()).liveStreams(any());
    }
}
