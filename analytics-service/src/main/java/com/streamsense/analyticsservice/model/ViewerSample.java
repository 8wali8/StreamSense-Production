package com.streamsense.analyticsservice.model;

/** One viewer-count reading for a session, from a Helix poll. */
public record ViewerSample(long sampledAt, int viewerCount) {}
