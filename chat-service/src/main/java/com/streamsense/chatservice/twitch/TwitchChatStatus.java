package com.streamsense.chatservice.twitch;

import java.util.List;

public record TwitchChatStatus(
        boolean enabled,
        TwitchChatState state,
        List<String> channels,
        long lastMessageAt,
        String lastError,
        long reconnectAttempts) {
}
