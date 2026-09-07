package com.streamsense.recommendationservice.client;

public record SponsorSignal(
        String detectionEventId,
        String sourceFrameId,
        String streamer,
        String frameRef,
        double frameSequence,
        long capturedAt,
        long processedAt,
        String sponsor,
        double confidence,
        String modelVersion,
        double x,
        double y,
        double width,
        double height) {}
