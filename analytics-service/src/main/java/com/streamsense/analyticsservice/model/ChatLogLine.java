package com.streamsense.analyticsservice.model;

/** One chat line of a supplied log, normalised: seconds into the recording, who said it, and what. */
public record ChatLogLine(double offsetSeconds, String user, String message) {}
