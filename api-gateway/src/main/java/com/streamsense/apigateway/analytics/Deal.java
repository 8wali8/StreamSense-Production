package com.streamsense.apigateway.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Deal(
        long id,
        String streamer,
        String sponsor,
        long startsAt,
        Long endsAt,
        Integer promisedStreams,
        Double fee,
        String currency,
        double cpmPer30sEquivalent,
        double hostReadRatePer1000,
        String trackedLink,
        String trackedLinkHost,
        String chatCommand,
        String channelPointReward,
        boolean active,
        String shareToken,
        long createdAt,
        List<DealLogo> logos) {

    /** A deal answered without logos (an older analytics-service, a test fixture) has none, not null. */
    public Deal {
        logos = logos == null ? List.of() : List.copyOf(logos);
    }

    /** The deal as a share link may see it: no fee, no token. */
    public Deal forSharedView() {
        return new Deal(
                id,
                streamer,
                sponsor,
                startsAt,
                endsAt,
                promisedStreams,
                null,
                currency,
                cpmPer30sEquivalent,
                hostReadRatePer1000,
                trackedLink,
                trackedLinkHost,
                chatCommand,
                channelPointReward,
                active,
                null,
                createdAt,
                logos);
    }
}
