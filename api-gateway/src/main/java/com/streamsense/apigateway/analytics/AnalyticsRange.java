package com.streamsense.apigateway.analytics;

import org.springframework.web.util.UriBuilder;

/** Which slice of time an analytics query is about: a whole session, an absolute range, or neither. */
public record AnalyticsRange(Long sessionId, Long from, Long to) {

    public static final AnalyticsRange NONE = new AnalyticsRange(null, null, null);

    /** From GraphQL arguments: the session id string and epoch-millis floats. A malformed id is ignored. */
    public static AnalyticsRange of(String sessionId, Double from, Double to) {
        Long id = null;
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                id = Long.parseLong(sessionId.trim());
            } catch (NumberFormatException ex) {
                id = null;
            }
        }
        return new AnalyticsRange(id, from == null ? null : from.longValue(), to == null ? null : to.longValue());
    }

    public void apply(UriBuilder builder) {
        if (sessionId != null) {
            builder.queryParam("sessionId", sessionId);
        }
        if (from != null) {
            builder.queryParam("from", from);
        }
        if (to != null) {
            builder.queryParam("to", to);
        }
    }
}
