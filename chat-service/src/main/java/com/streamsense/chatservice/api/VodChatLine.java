package com.streamsense.chatservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** One line of a supplied chat log: seconds into the recording, who said it, and what. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodChatLine(
        @NotNull @PositiveOrZero Double offsetSeconds, @NotBlank String user, @NotNull String message) {}
