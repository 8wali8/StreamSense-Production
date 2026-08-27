package com.streamsense.analyticsservice.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class AnalyticsMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> lagByTopic = new ConcurrentHashMap<>();

    public AnalyticsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void eventProcessed(String topic) {
        counter("streamsense.analytics.events.processed", topic).increment();
    }

    public void eventDuplicate(String topic) {
        counter("streamsense.analytics.events.duplicate", topic).increment();
    }

    public void eventFailed(String topic) {
        counter("streamsense.analytics.events.failed", topic).increment();
    }

    public void bucketUpdated(String metric) {
        Counter.builder("streamsense.analytics.bucket.updates")
                .tag("metric", metric)
                .register(meterRegistry)
                .increment();
    }

    public void recordLag(String topic, long lagMs) {
        lagByTopic.computeIfAbsent(topic, key -> {
            AtomicLong value = new AtomicLong();
            Gauge.builder("streamsense.analytics.lag.ms", value, AtomicLong::get)
                    .tag("topic", key)
                    .register(meterRegistry);
            return value;
        }).set(Math.max(0, lagMs));
    }

    private Counter counter(String name, String topic) {
        return Counter.builder(name)
                .tag("topic", topic)
                .register(meterRegistry);
    }
}
