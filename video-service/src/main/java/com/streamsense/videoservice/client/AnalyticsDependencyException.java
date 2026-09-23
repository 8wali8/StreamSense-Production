package com.streamsense.videoservice.client;

/** analytics-service could not say what the channel's current deal is. */
public class AnalyticsDependencyException extends RuntimeException {

    public AnalyticsDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
