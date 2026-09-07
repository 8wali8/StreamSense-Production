package com.streamsense.sentimentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SentimentMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer mlLatencyTimer;
    private final Timer persistenceLatencyTimer;
    private final Timer endToEndLatencyTimer;

    public SentimentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.mlLatencyTimer = Timer.builder("streamsense_ml_sentiment_latency_ms")
                .description("Latency of ML sentiment inference calls")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.persistenceLatencyTimer = Timer.builder("streamsense_sentiment_persistence_latency_ms")
                .description("Latency of sentiment persistence writes")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.endToEndLatencyTimer = Timer.builder("streamsense_sentiment_end_to_end_latency_ms")
                .description("End-to-end latency from chat ingest timestamp to processed sentiment event")
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

    public <T> T recordPersistenceLatency(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            persistenceLatencyTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    public void recordEndToEndLatency(long durationMs) {
        endToEndLatencyTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public <T> T recordHistoryLookup(String cache, String source, Supplier<T> supplier) {
        return Timer.builder("streamsense_history_lookup_latency_ms")
                .description("Latency of history lookups by cache and source")
                .tag("cache", cache)
                .tag("source", source)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(supplier);
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

    public void incrementFallback(String reason) {
        Counter.builder("streamsense_sentiment_fallback_total")
                .description("Total number of fallback sentiment results")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void incrementProtectedCall(String outcome) {
        Counter.builder("streamsense_ml_protected_calls_total")
                .description("Total protected ML sentiment call outcomes")
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    public void incrementDeadLetter() {
        Counter.builder("streamsense_sentiment_dead_letter_total")
                .description("Total number of dead-lettered sentiment source events")
                .register(meterRegistry)
                .increment();
    }

    public void incrementRetry() {
        Counter.builder("streamsense_sentiment_retry_total")
                .description("Total number of sentiment processing retries")
                .register(meterRegistry)
                .increment();
    }

    public void incrementCacheHit(String cache) {
        Counter.builder("streamsense_cache_hits_total")
                .description("Total number of cache hits")
                .tag("cache", cache)
                .register(meterRegistry)
                .increment();
    }

    public void incrementCacheMiss(String cache) {
        Counter.builder("streamsense_cache_misses_total")
                .description("Total number of cache misses")
                .tag("cache", cache)
                .register(meterRegistry)
                .increment();
    }
}
