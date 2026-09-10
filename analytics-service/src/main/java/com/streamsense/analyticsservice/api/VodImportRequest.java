package com.streamsense.analyticsservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;

/**
 * Twitch keeps no concurrent viewer history for a recording, so the streamer may supply the
 * average viewers from their dashboard; without it the imported session cannot be priced.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VodImportRequest(@Min(1) Integer averageViewers) {}
