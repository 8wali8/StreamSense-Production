package com.streamsense.analyticsservice.model;

/**
 * What happened to the sampled frames in a window, summed over every sponsor: how many there were,
 * how many were examined for a logo, how many could not be (the fallback path), and how many were
 * not looked at because the deal had no logo.
 */
public record SponsorOutcomeTotals(long frames, long examined, long unavailable, long noLogo) {

    public static final SponsorOutcomeTotals NONE = new SponsorOutcomeTotals(0, 0, 0, 0);
}
