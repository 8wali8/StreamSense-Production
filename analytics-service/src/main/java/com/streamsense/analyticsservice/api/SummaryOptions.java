package com.streamsense.analyticsservice.api;

/**
 * Per-request inputs for a session summary. Every field is optional: the sponsor defaults to the
 * most visible one in the session, the command and link host to the configured defaults, and the
 * rates to configuration. Deals carry these once they exist.
 */
public record SummaryOptions(
        String sponsor,
        String chatCommand,
        String trackedLinkHost,
        Double cpmPer30sEquivalent,
        Double hostReadRatePer1000) {}
