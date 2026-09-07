package com.streamsense.analyticsservice.api;

import java.util.List;

public record BrandSafetyMetrics(String level, Double score, List<RiskFactor> factors) {}
