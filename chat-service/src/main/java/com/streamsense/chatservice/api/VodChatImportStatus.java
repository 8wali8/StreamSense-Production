package com.streamsense.chatservice.api;

/** Progress of one recording's chat import. */
public record VodChatImportStatus(
        String vodId, String channel, String state, int published, int total, String lastError, long updatedAt) {}
