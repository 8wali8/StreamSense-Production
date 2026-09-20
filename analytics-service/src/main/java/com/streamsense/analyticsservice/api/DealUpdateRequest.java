package com.streamsense.analyticsservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A deal's terms as they should be from now on: the same fields as {@link DealCreateRequest}, all of
 * them, without the streamer, which is the deal's and never changes. A term left out is cleared (or,
 * for a rate, returns to the configured default), so a client sends the whole deal back, not a patch.
 * The share token is untouched; the share routes own it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DealUpdateRequest(
        @NotBlank @Size(max = 255) String sponsor,
        @NotNull Long startsAt,
        Long endsAt,
        @Min(1) Integer promisedStreams,
        @Min(0) Double fee,
        @Size(max = 8) String currency,
        @Min(0) Double cpmPer30sEquivalent,
        @Min(0) Double hostReadRatePer1000,
        @Size(max = 1024) String trackedLink,
        @Size(max = 64) String chatCommand,
        @Size(max = 255) String channelPointReward) {}
