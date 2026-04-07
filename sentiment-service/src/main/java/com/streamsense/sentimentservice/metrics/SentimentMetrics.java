package com.streamsense.sentimentservice.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class SentimentMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer mlLatencyTimer;

    public SentimentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.mlLatencyTimer = Timer.builder("streamsense_ml_sentiment_latency_ms")
                .description("Latency of ML sentiment inference calls")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public <T> T recordMlLatency(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            mlLatencyTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    public void incrementProcessed(String label) {
        Counter.builder("streamsense_sentiment_events_total")
                .description("Total number of processed sentiment events")
                .tag("label", label)
                .register(meterRegistry)
                .increment();
    }

    public void incrementPersistence(String status) {
        Counter.builder("streamsense_sentiment_persistence_total")
                .description("Total sentiment persistence attempts by outcome")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
}
