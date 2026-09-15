package com.streamsense.chatservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.streamsense.chatservice.config.StreamSenseProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwitchChatLifecycleServiceTest {

    @Mock
    private TwitchIrcMessageParser parser;

    @Mock
    private TwitchChatMessageHandler handler;

    @Mock
    private TwitchChatMetrics metrics;

    @Mock
    private TwitchVodChatReplayService replayService;

    @Test
    void start_marksDisabledWhenTwitchChatDisabled() {
        StreamSenseProperties properties = new StreamSenseProperties();
        properties.getTwitch().getChat().setEnabled(false);

        TwitchChatLifecycleService service =
                new TwitchChatLifecycleService(properties, parser, handler, metrics, replayService);

        service.start();

        verify(metrics).markDisabled();
    }

    @Test
    void start_failsFastWhenEnabledWithoutUsername() {
        StreamSenseProperties properties = enabledProperties();
        properties.getTwitch().getChat().setUsername("");

        TwitchChatLifecycleService service =
                new TwitchChatLifecycleService(properties, parser, handler, metrics, replayService);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username is missing");
    }

    @Test
    void start_failsFastWhenEnabledWithoutOauthToken() {
        StreamSenseProperties properties = enabledProperties();
        properties.getTwitch().getChat().setOauthToken("");

        TwitchChatLifecycleService service =
                new TwitchChatLifecycleService(properties, parser, handler, metrics, replayService);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth token is missing");
    }

    @Test
    void start_waitsForRuntimeChannelWhenEnabledWithoutChannels() {
        StreamSenseProperties properties = enabledProperties();
        properties.getTwitch().getChat().setChannels(List.of());

        TwitchChatLifecycleService service =
                new TwitchChatLifecycleService(properties, parser, handler, metrics, replayService);

        service.start();

        verify(metrics).markStopped();
    }

    @Test
    void start_allowsReplayChannelWithoutTwitchCredentials() {
        StreamSenseProperties properties = new StreamSenseProperties();
        StreamSenseProperties.Chat chat = properties.getTwitch().getChat();
        chat.setEnabled(true);
        chat.setChannels(List.of("redbull-testing"));
        when(replayService.isReplayChannel("redbull-testing")).thenReturn(true);
        when(replayService.start(List.of("redbull-testing"))).thenReturn(List.of("redbull-testing"));

        TwitchChatLifecycleService service =
                new TwitchChatLifecycleService(properties, parser, handler, metrics, replayService);

        service.start();

        verify(replayService).start(List.of("redbull-testing"));
        verify(metrics).markConnected();
    }

    @Test
    void joinChannel_addsOneChannelAndKeepsTheOthers() {
        StreamSenseProperties properties = replayProperties("redbull-testing");
        when(replayService.isReplayChannel(anyString())).thenReturn(true);
        TwitchChatLifecycleService service = service(properties);
        service.start();

        service.joinChannel("@Ninja");

        assertThat(properties.getTwitch().getChat().getChannels()).containsExactly("redbull-testing", "ninja");
        assertThat(service.isJoined("ninja")).isTrue();
    }

    @Test
    void joinChannel_isIdempotent() {
        StreamSenseProperties properties = replayProperties("redbull-testing");
        TwitchChatLifecycleService service = service(properties);

        service.joinChannel("redbull-testing");

        assertThat(properties.getTwitch().getChat().getChannels()).containsExactly("redbull-testing");
    }

    @Test
    void joinChannel_isRefusedWhenTheConnectorIsFull() {
        StreamSenseProperties properties = replayProperties("redbull-testing");
        properties.getTwitch().getChat().setMaxChannels(1);
        TwitchChatLifecycleService service = service(properties);

        assertThatThrownBy(() -> service.joinChannel("ninja"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already measuring");
    }

    @Test
    void joinChannel_isRefusedWhenIngestIsDisabled() {
        StreamSenseProperties properties = new StreamSenseProperties();
        TwitchChatLifecycleService service = service(properties);

        assertThatThrownBy(() -> service.joinChannel("ninja"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void joinChannel_refusesABlankChannel() {
        TwitchChatLifecycleService service = service(replayProperties("redbull-testing"));

        assertThatThrownBy(() -> service.joinChannel(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("channel is required");
    }

    @Test
    void partChannel_leavesTheOtherChannelsBeingMeasured() {
        StreamSenseProperties properties = replayProperties("redbull-testing", "second-replay");
        when(replayService.isReplayChannel(anyString())).thenReturn(true);
        TwitchChatLifecycleService service = service(properties);
        service.start();

        service.partChannel("second-replay");

        assertThat(properties.getTwitch().getChat().getChannels()).containsExactly("redbull-testing");
        assertThat(service.isJoined("second-replay")).isFalse();
    }

    @Test
    void partChannel_ofTheLastChannelLeavesIngestWaiting() {
        StreamSenseProperties properties = replayProperties("redbull-testing");
        when(replayService.isReplayChannel("redbull-testing")).thenReturn(true);
        TwitchChatLifecycleService service = service(properties);
        service.start();

        service.partChannel("redbull-testing");

        assertThat(properties.getTwitch().getChat().getChannels()).isEmpty();
        verify(metrics, atLeastOnce()).markStopped();
    }

    @Test
    void partChannel_isANoOpForAChannelThatIsNotJoined() {
        StreamSenseProperties properties = replayProperties("redbull-testing");
        TwitchChatLifecycleService service = service(properties);

        service.partChannel("ninja");

        assertThat(properties.getTwitch().getChat().getChannels()).containsExactly("redbull-testing");
    }

    @Test
    void start_refusesMoreConfiguredChannelsThanTheCap() {
        StreamSenseProperties properties = replayProperties("one", "two", "three");
        properties.getTwitch().getChat().setMaxChannels(2);
        TwitchChatLifecycleService service = service(properties);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("more than max-channels");
    }

    @Test
    void theCapCountsChannels_notRepeatsOfOne() {
        // Ten spellings of one login are one channel; the cap is about what IRC is measuring.
        StreamSenseProperties properties = replayProperties("redbull-testing");
        properties.getTwitch().getChat().setMaxChannels(2);
        when(replayService.isReplayChannel(anyString())).thenReturn(true);
        TwitchChatLifecycleService service = service(properties);
        service.start();

        service.joinChannel("@Ninja");
        service.joinChannel("#ninja");
        service.joinChannel("ninja");

        assertThat(properties.getTwitch().getChat().getChannels()).containsExactly("redbull-testing", "ninja");
    }

    private TwitchChatLifecycleService service(StreamSenseProperties properties) {
        return new TwitchChatLifecycleService(properties, parser, handler, metrics, replayService);
    }

    /** Replay channels only, so no test opens an IRC socket. */
    private static StreamSenseProperties replayProperties(String... channels) {
        StreamSenseProperties properties = new StreamSenseProperties();
        StreamSenseProperties.Chat chat = properties.getTwitch().getChat();
        chat.setEnabled(true);
        chat.setChannels(List.of(channels));
        return properties;
    }

    private static StreamSenseProperties enabledProperties() {
        StreamSenseProperties properties = new StreamSenseProperties();
        StreamSenseProperties.Chat chat = properties.getTwitch().getChat();
        chat.setEnabled(true);
        chat.setUsername("botuser");
        chat.setOauthToken("oauth:test-token");
        chat.setChannels(List.of("testchannel"));
        return properties;
    }
}
