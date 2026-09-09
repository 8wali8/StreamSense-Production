package com.streamsense.analyticsservice.api;

/**
 * One sponsorship deal as the API reports it. {@code fee} is what the streamer is paid and is
 * private to them; {@code active} is true while the dates cover now. {@code trackedLinkHost} is the
 * host of the tracked link, which is what chat posts are matched on.
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
        long createdAt) {}
