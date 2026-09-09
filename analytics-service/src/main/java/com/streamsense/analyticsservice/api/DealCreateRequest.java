package com.streamsense.analyticsservice.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new deal. The rates default to the configured media value assumptions when absent; the fee,
 * dates' end, promised stream count, link, command, and reward are optional.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DealCreateRequest(
        @NotBlank @Size(max = 255) String streamer,
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
