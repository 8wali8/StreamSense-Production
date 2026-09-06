package com.streamsense.chatservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.streamsense.chatservice.api.ChatIngestRequest;
import com.streamsense.chatservice.kafka.ChatKafkaProducer;
import com.streamsense.chatservice.metrics.ChatMetrics;
import com.streamsense.chatservice.service.ChatEventIngestService;
import com.streamsense.chatservice.twitch.TwitchChatMessageHandler;
import com.streamsense.chatservice.twitch.TwitchChatMetrics;
import com.streamsense.chatservice.twitch.TwitchIrcChatMessage;

/** Every ChatMessageEvent this service publishes conforms to docs/schemas/chat-message-event.schema.json. */
class EventContractTest {

    private static final String SCHEMA = "chat-message-event.schema.json";

    @Test
    void publishedChatMessageMatchesSchema() {
        ChatMessageEvent event = new ChatMessageEvent("evt-1", "streamer-1", "user-1", "hello", 1710000000000L);
        event.setSource("TWITCH");
        event.setChannelLogin("streamer-1");

        assertThat(EventSchemas.violations(SCHEMA, EventSchemas.toJson(event))).isEmpty();
    }

    @Test
    void minimalChatMessageWithoutSessionFieldsStillMatchesSchema() {
        ChatMessageEvent event = new ChatMessageEvent("evt-1", "streamer-1", "user-1", "hello", 1710000000000L);

        assertThat(EventSchemas.violations(SCHEMA, EventSchemas.toJson(event))).isEmpty();
    }

    @Test
    void schemaRejectsAnEventWithoutAMessage() {
        ChatMessageEvent event = new ChatMessageEvent("evt-1", "streamer-1", "user-1", null, 1710000000000L);

        assertThat(EventSchemas.violations(SCHEMA, EventSchemas.toJson(event))).isNotEmpty();
    }

    @Test
    void twitchIrcMessagesAreTaggedWithSourceAndChannel() {
        ChatEventIngestService ingestService = mock(ChatEventIngestService.class);
        TwitchChatMessageHandler handler = new TwitchChatMessageHandler(ingestService, mock(TwitchChatMetrics.class));

        handler.handle(new TwitchIrcChatMessage("austincs", "user1", "hello", "msg-1", 1710000000000L));

        ArgumentCaptor<ChatMessageEvent> captor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(ingestService).ingestTwitch(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo("TWITCH");
        assertThat(captor.getValue().getChannelLogin()).isEqualTo("austincs");
        assertThat(EventSchemas.violations(SCHEMA, EventSchemas.toJson(captor.getValue()))).isEmpty();
    }

    @Test
    void syntheticMessagesAreTaggedAsManual() {
        ChatKafkaProducer producer = mock(ChatKafkaProducer.class);
        ChatEventIngestService ingestService = new ChatEventIngestService(producer, mock(ChatMetrics.class));
        ChatIngestRequest request = new ChatIngestRequest();
        request.setStreamer("streamer-1");
        request.setUser("user-1");
        request.setMessage("hello");
        request.setTimestamp(1710000000000L);

        ingestService.ingestSynthetic(request, null, null);

        ArgumentCaptor<ChatMessageEvent> captor = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(producer).publish(captor.capture(), isNull(), isNull());
        assertThat(captor.getValue().getSource()).isEqualTo("MANUAL");
        assertThat(captor.getValue().getChannelLogin()).isEqualTo("streamer-1");
        assertThat(EventSchemas.violations(SCHEMA, EventSchemas.toJson(captor.getValue()))).isEmpty();
    }
}
