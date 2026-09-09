package com.streamsense.analyticsservice.model;

/** Sponsor-relevant sentiment for one channel (CHAT or VOICE) summed over a range. */
public record SponsorMentionTotals(
        String channel,
        long mentionCount,
        long positiveCount,
        long neutralCount,
        long negativeCount,
        double scoreSum) {}
