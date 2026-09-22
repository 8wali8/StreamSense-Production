package com.streamsense.analyticsservice.model;

/** One deal_logos row as stored: the logo's object in storage and what was measured when it was uploaded. */
public record DealLogoRow(
        long id,
        long dealId,
        String objectKey,
        String contentType,
        String sha256,
        int width,
        int height,
        long sizeBytes,
        long uploadedAt) {}
