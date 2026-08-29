package com.streamsense.apigateway.model;

import java.util.List;

public record Recommendation(
        String recommendationId,
        String streamer,
        String title,
        String category,
        double score,
        String reasonSummary,
        List<String> reasons,
        String experimentName,
        String variantId,
        long generatedAt) {}
