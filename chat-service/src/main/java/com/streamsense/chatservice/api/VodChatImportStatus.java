package com.streamsense.chatservice.api;

/**
 * Progress of one recording's chat import. {@code offsetSeconds} is how far into the recording the
 * last published comment sat, so a stopped import can resume from there; {@code stopRequested} is
 * set from the moment a stop is asked for until the import thread confirms it with state STOPPED.
 */
public record VodChatImportStatus(
        String vodId,
        String channel,
        String state,
        int published,
        int total,
        double offsetSeconds,
        boolean stopRequested,
        String lastError,
        long updatedAt) {}
