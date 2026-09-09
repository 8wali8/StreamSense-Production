package com.streamsense.analyticsservice.model;

/** One deals row as stored. */
public record DealRow(
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
        String chatCommand,
        String channelPointReward,
        long createdAt) {

    /** True while the deal's dates cover the instant. */
    public boolean covers(long at) {
        return startsAt <= at && (endsAt == null || at < endsAt);
    }
}
