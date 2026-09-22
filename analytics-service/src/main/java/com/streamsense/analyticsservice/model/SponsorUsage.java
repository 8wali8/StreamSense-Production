package com.streamsense.analyticsservice.model;

/**
 * One sponsor as deals name it, grouped without regard to case: how many deals and channels name
 * it, and when the newest of those deals starts. What the operator's catalog compares itself to.
 */
public record SponsorUsage(String sponsor, long deals, long channels, long latestStartsAt) {}
