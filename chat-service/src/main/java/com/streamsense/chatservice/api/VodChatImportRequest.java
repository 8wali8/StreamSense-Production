package com.streamsense.chatservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Publish a recording's chat from the lines a streamer supplied, with the original timestamps: each
 * line lands at {@code baseTimeMs + offset}, tagged with the session key the importer chose. A resumed
 * import names the offset (seconds into the recording) it continues from; the lines before it are
 * expected to have been left out by the caller.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodChatImportRequest(
        @NotBlank String channel,
        @NotBlank String vodId,
        @NotNull Long baseTimeMs,
        @NotBlank String streamSessionId,
        @Min(0) Long startOffsetSeconds,
        @NotEmpty List<@Valid VodChatLine> comments) {

    public long startOffsetOrZero() {
        return startOffsetSeconds == null ? 0L : startOffsetSeconds;
    }
}
