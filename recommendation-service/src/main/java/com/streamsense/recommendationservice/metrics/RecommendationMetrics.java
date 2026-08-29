package com.streamsense.recommendationservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class RecommendationMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer recommendationLatency;

    public RecommendationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.recommendationLatency = Timer.builder("streamsense_recommendation_latency_ms")
                .description("Latency of recommendation generation requests")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public <T> T recordGeneration(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            recommendationLatency.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    public void incrementServed(String experimentName, String variantId, int count) {
        Counter.builder("streamsense_recommendations_served_total")
                .description("Total number of recommendations served")
                .tag("experiment", experimentName)
                .tag("variant", variantId)
                .register(meterRegistry)
                .increment(count);
    }

    public void incrementVariantSelection(String experimentName, String variantId) {
        Counter.builder("streamsense_experiment_variant_total")
                .description("Total number of recommendation requests by experiment variant")
                .tag("experiment", experimentName)
                .tag("variant", variantId)
                .register(meterRegistry)
                .increment();
    }
}
