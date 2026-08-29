package com.streamsense.apigateway.analytics;

import java.util.List;

public record BrandSafetyMetrics(String level, Double score, List<RiskFactor> factors) {}
