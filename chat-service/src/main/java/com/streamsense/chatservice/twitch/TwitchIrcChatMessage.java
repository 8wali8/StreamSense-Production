package com.streamsense.chatservice.twitch;

public record TwitchIrcChatMessage(String channel, String user, String message, String messageId, long timestamp) {}
