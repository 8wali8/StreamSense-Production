package com.streamsense.analyticsservice.api;

/**
 * What the sponsor's exposure would have cost to buy. Null values mean the session has no viewer
 * samples yet, so nothing can be priced; the rates are always reported so the reader can see the
 * assumptions. Sentiment is never folded into these figures.
 */
public record SessionValue(
        Double logoValue,
        Double hostReadValue,
        Double mediaValue,
        Double weightedLogoViewerMinutes,
        Double averageProminence,
        double cpmPer30sEquivalent,
        double hostReadRatePer1000,
        String basis) {}
