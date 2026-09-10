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
    }

    @Test
    void aTwitchFailureIsLoggedAndTheNextPollStillRuns() {
        config.setChannels(List.of("racer"));
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        when(helix.liveStreams(any())).thenThrow(new IllegalStateException("twitch down"));
        StreamSessionPoller poller = new StreamSessionPoller(helix, sessions, List::of, config, clock);

        poller.poll();

        verify(sessions, never()).closeHelixSessionsNotLive(any(), any());
    }

    @Test
    void nothingToWatchMeansNoCall() {
        when(sessions.streamersSeenSince(anyLong())).thenReturn(List.of());
        new StreamSessionPoller(helix, sessions, List::of, config, clock).poll();
        verify(helix, never()).liveStreams(any());
    }
}
