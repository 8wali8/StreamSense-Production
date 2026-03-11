package com.streamsense.chatservice.metrics;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class ChatMetrics {

    private final Counter chatIngestCounter;
    private final Timer kafkaProduceTimer;

    public ChatMetrics(MeterRegistry meterRegistry) {
        System.out.println("ChatMetrics bean initialized");
        this.chatIngestCounter = Counter.builder("streamsense_chat_ingest_total")
                .description("Total number of accepted chat ingest requests")
                .register(meterRegistry);

        this.kafkaProduceTimer = Timer.builder("streamsense_kafka_produce_latency_ms")
                .description("Latency of producing chat messages to Kafka")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    public void incrementChatIngest() {
        chatIngestCounter.increment();
    }

    public <T> T recordKafkaProduce(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            long durationNs = System.nanoTime() - start;
            kafkaProduceTimer.record(durationNs, TimeUnit.NANOSECONDS);
        }
    }

    public void recordKafkaProduce(Runnable runnable) {
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            long durationNs = System.nanoTime() - start;
            kafkaProduceTimer.record(durationNs, TimeUnit.NANOSECONDS);
        }
    }
}