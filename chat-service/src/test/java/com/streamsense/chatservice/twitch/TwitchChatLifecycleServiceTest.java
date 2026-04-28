package com.streamsense.chatservice.twitch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.streamsense.chatservice.config.StreamSenseProperties;

@ExtendWith(MockitoExtension.class)
class TwitchChatLifecycleServiceTest {

    @Mock
    private TwitchIrcMessageParser parser;

    @Mock
    private TwitchChatMessageHandler handler;

    @Mock
    private TwitchChatMetrics metrics;

    @Test
    void start_marksDisabledWhenTwitchChatDisabled() {
        StreamSenseProperties properties = new StreamSenseProperties();
        properties.getTwitch().getChat().setEnabled(false);

        TwitchChatLifecycleService service = new TwitchChatLifecycleService(properties, parser, handler, metrics);

        service.start();

        verify(metrics).markDisabled();
    }

    @Test
    void start_failsFastWhenEnabledWithoutUsername() {
        StreamSenseProperties properties = enabledProperties();
        properties.getTwitch().getChat().setUsername("");

        TwitchChatLifecycleService service = new TwitchChatLifecycleService(properties, parser, handler, metrics);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("username is missing");
    }

    @Test
    void start_failsFastWhenEnabledWithoutOauthToken() {
        StreamSenseProperties properties = enabledProperties();
        properties.getTwitch().getChat().setOauthToken("");

        TwitchChatLifecycleService service = new TwitchChatLifecycleService(properties, parser, handler, metrics);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OAuth token is missing");
    }

    @Test
    void start_failsFastWhenEnabledWithoutChannels() {
        StreamSenseProperties properties = enabledProperties();
        properties.getTwitch().getChat().setChannels(List.of());

        TwitchChatLifecycleService service = new TwitchChatLifecycleService(properties, parser, handler, metrics);

        assertThatThrownBy(service::start)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no channels are configured");
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
