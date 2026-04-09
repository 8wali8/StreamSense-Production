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

    public VideoMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.sponsorLatencyTimer = Timer.builder("streamsense_sponsor_inference_latency_ms")
                .description("Latency of ML sponsor inference calls")
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

    public void incrementFramesIngested() {
        Counter.builder("streamsense_frames_ingested_total")
                .description("Total number of ingested video frames")
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
}
