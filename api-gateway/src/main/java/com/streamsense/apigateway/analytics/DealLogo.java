package com.streamsense.apigateway.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One logo on a deal, as analytics-service reports it; the storage ref stays with the services. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DealLogo(long id, String contentType, int width, int height, long sizeBytes, long uploadedAt) {}
