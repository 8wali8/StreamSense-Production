package com.streamsense.analyticsservice.api;

/**
 * One logo on a deal, as the API reports it. {@code ref} is the object's storage URI
 * ({@code s3://bucket/key}), what the detection pipeline will read the image from; the console
 * fetches the bytes through {@code GET /api/analytics/deals/{id}/logos/{logoId}} instead.
 */
public record DealLogo(
        long id, long dealId, String contentType, int width, int height, long sizeBytes, long uploadedAt, String ref) {}
