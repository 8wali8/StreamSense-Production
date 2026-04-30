package com.streamsense.videoservice.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class VideoMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer sponsorLatencyTimer;
    private final Timer persistenceLatencyTimer;
    private final Timer endToEndLatencyTimer;

    public VideoMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.sponsorLatencyTimer = Timer.builder("streamsense_sponsor_inference_latency_ms")
                .description("Latency of ML sponsor inference calls")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.persistenceLatencyTimer = Timer.builder("streamsense_sponsor_persistence_latency_ms")
                .description("Latency of sponsor detection persistence writes")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.endToEndLatencyTimer = Timer.builder("streamsense_sponsor_end_to_end_latency_ms")
                .description("End-to-end latency from frame capture to processed sponsor detection event")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public <T> T recordInferenceLatency(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            sponsorLatencyTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
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

    public void incrementFramesIngested() {
        Counter.builder("streamsense_frames_ingested_total")
                .description("Total number of ingested video frames")
                .register(meterRegistry)
                .increment();
    }

    public void incrementFramesFromTwitch() {
        Counter.builder("streamsense_video_frames_from_twitch_total")
                .description("Total number of video frames captured from Twitch")
                .register(meterRegistry)
                .increment();
    }

    public void incrementSponsorDetection(String sponsor) {
        Counter.builder("streamsense_sponsor_detections_total")
                .description("Total number of sponsor detection events")
                .tag("sponsor", sponsor)
                .register(meterRegistry)
                .increment();
    }

    public void incrementSponsorFallback(String reason) {
        Counter.builder("streamsense_sponsor_fallback_total")
                .description("Total number of fallback sponsor detections")
                .tag("reason", reason)
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
