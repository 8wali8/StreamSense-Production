package com.streamsense.chatservice.twitch;

public record TwitchVodChatComment(
        String id,
        String user,
        String message,
        double offsetSeconds) {
}
