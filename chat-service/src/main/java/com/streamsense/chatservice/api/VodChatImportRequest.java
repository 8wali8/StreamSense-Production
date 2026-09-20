package com.streamsense.chatservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Publish a recording's chat with its original timestamps: each comment lands at
 * {@code baseTimeMs + offset}, tagged with the session key the importer chose. A resumed import
 * names the offset (seconds into the recording) to start paging from; absent or zero means the start.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodChatImportRequest(
        @NotBlank String channel,
        @NotBlank String vodId,
        @NotNull Long baseTimeMs,
        @NotBlank String streamSessionId,
        @Min(0) Long startOffsetSeconds) {

    public long startOffsetOrZero() {
        return startOffsetSeconds == null ? 0L : startOffsetSeconds;
    }
}
