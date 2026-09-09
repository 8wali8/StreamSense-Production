package com.streamsense.analyticsservice.api;

/** What viewers did in chat for the deal's command and tracked link. Null names mean none was configured. */
public record DirectResponse(
        String chatCommand, long commandUses, long commandUsers, String trackedLinkHost, long linkPosts) {}
