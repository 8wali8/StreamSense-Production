package com.streamsense.chatservice.twitch;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.service.ChatEventIngestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TwitchChatMessageHandlerTest {

    @Mock
    private ChatEventIngestService ingestService;

    @Mock
    private TwitchChatMetrics metrics;

    @InjectMocks
    private TwitchChatMessageHandler handler;

    @Test
    void handle_publishesTwitchMessageAsChatEvent() {
        handler.handle(new TwitchIrcChatMessage("channel", "user1", "hello", "msg-1", 1710000000000L));

        verify(ingestService)
                .ingestTwitch(
                        argThat((ChatMessageEvent event) -> event.getEventId().equals("msg-1")
                                && event.getStreamer().equals("channel")
                                && event.getUser().equals("user1")
                                && event.getMessage().equals("hello")
                                && event.getTimestamp() == 1710000000000L));
        verify(metrics).recordMessage();
    }

    @Test
    void handle_ignoresDuplicateTwitchMessageIds() {
        TwitchIrcChatMessage message = new TwitchIrcChatMessage("channel", "user1", "hello", "msg-1", 1710000000000L);

        handler.handle(message);
        handler.handle(message);

        verify(ingestService).ingestTwitch(argThat((ChatMessageEvent event) -> event.getEventId()
                .equals("msg-1")));
        verify(metrics).recordDuplicate();
    }

    @Test
    void handle_acceptsMessagesWithoutExternalIds() {
        handler.handle(new TwitchIrcChatMessage("channel", "user1", "hello", null, 1710000000000L));
        handler.handle(new TwitchIrcChatMessage("channel", "user1", "hello again", null, 1710000000001L));

        verify(metrics, never()).recordDuplicate();
    }
}
