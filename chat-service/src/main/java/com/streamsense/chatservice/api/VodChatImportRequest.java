package com.streamsense.chatservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Publish a recording's chat with its original timestamps: each comment lands at
 * {@code baseTimeMs + offset}, tagged with the session key the importer chose.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodChatImportRequest(
        @NotBlank String channel, @NotBlank String vodId, @NotNull Long baseTimeMs, @NotBlank String streamSessionId) {}
