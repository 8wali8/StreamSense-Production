package com.streamsense.analyticsservice.api;

/** The read-only share token of a deal. */
public record ShareLink(long dealId, String token) {}
