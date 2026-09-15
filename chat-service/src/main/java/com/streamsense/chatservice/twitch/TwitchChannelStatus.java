package com.streamsense.chatservice.twitch;

/**
 * Whether one channel's chat is being ingested, and how the connector behind it is doing. The per-channel
 * read a streamer is allowed: it says nothing about the other channels being measured.
 */
public record TwitchChannelStatus(String channel, boolean joined, boolean enabled, TwitchChatState state) {}
