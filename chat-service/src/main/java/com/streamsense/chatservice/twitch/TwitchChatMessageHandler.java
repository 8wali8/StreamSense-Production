package com.streamsense.chatservice.twitch;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.service.ChatEventIngestService;

@Component
public class TwitchChatMessageHandler {

    private static final int MAX_SEEN_MESSAGE_IDS = 5000;

    private final ChatEventIngestService ingestService;
    private final TwitchChatMetrics metrics;
    private final Map<String, Boolean> seenMessageIds = new LinkedHashMap<>(MAX_SEEN_MESSAGE_IDS, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_SEEN_MESSAGE_IDS;
        }
    };

    public TwitchChatMessageHandler(ChatEventIngestService ingestService, TwitchChatMetrics metrics) {
        this.ingestService = ingestService;
        this.metrics = metrics;
    }

    public void handle(TwitchIrcChatMessage message) {
        if (isDuplicate(message.messageId())) {
            metrics.recordDuplicate();
            return;
        }

        String eventId = message.messageId() == null || message.messageId().isBlank()
                ? UUID.randomUUID().toString()
                : message.messageId();

        ChatMessageEvent event = new ChatMessageEvent(
                eventId,
                message.channel(),
                message.user(),
                message.message(),
                message.timestamp());
        event.setSource("TWITCH");
        event.setChannelLogin(message.channel());

        ingestService.ingestTwitch(event);
        metrics.recordMessage();
    }

    private boolean isDuplicate(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        synchronized (seenMessageIds) {
            if (seenMessageIds.containsKey(messageId)) {
                return true;
            }
            seenMessageIds.put(messageId, Boolean.TRUE);
            return false;
        }
    }
}
