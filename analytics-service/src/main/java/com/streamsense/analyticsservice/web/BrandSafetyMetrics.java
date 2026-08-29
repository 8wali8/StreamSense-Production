package com.streamsense.analyticsservice.web;

import java.util.List;

public record BrandSafetyMetrics(String level, Double score, List<RiskFactor> factors) {}
