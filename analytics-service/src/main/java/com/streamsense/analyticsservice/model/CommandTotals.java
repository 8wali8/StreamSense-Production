package com.streamsense.analyticsservice.model;

/** Uses of one chat command over a range, and how many different people used it. */
public record CommandTotals(long uses, long users) {}
