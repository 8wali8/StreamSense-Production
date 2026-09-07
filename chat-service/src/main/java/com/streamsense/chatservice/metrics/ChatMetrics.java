package com.streamsense.chatservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter chatIngestCounter;
    private final Timer kafkaProduceTimer;
    private final Counter kafkaProduceFailureCounter;

    public ChatMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.chatIngestCounter = Counter.builder("streamsense_chat_ingest_total")
                .description("Total number of accepted chat ingest requests")
                .register(meterRegistry);

        this.kafkaProduceTimer = Timer.builder("streamsense_kafka_produce_latency_ms")
                .description("Time from handing a chat message to the Kafka producer until the broker acknowledges it")
                .publishPercentileHistogram()
                .register(meterRegistry);

        this.kafkaProduceFailureCounter = Counter.builder("streamsense_kafka_produce_failures_total")
                .description("Total number of chat messages the Kafka producer failed to deliver")
                .register(meterRegistry);
    }

    public void incrementChatIngest() {
        chatIngestCounter.increment();
    }

    public Timer.Sample startKafkaProduce() {
        return Timer.start(meterRegistry);
    }

    public void recordKafkaProduce(Timer.Sample sample, Throwable failure) {
        sample.stop(kafkaProduceTimer);
        if (failure != null) {
            kafkaProduceFailureCounter.increment();
        }
    }
}
