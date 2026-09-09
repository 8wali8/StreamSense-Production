package com.streamsense.analyticsservice.api;

/**
 * One sponsorship deal as the API reports it. {@code fee} is what the streamer is paid and is
 * private to them; {@code active} is true while the dates cover now. {@code trackedLinkHost} is the
 * host of the tracked link, which is what chat posts are matched on. {@code shareToken} is the
 * read-only share token when the deal is shared; like the fee it is private to the streamer.
 */
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
        long createdAt) {}
