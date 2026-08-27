package com.streamsense.chatservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.metrics.ChatMetrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ChatKafkaProducerTest {

    private SimpleMeterRegistry registry;
    private KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private ChatKafkaProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        registry = new SimpleMeterRegistry();
        kafkaTemplate = mock(KafkaTemplate.class);
        producer = new ChatKafkaProducer(kafkaTemplate, "stream.chat.messages", new ChatMetrics(registry));
    }

    @Test
    @SuppressWarnings("unchecked")
    void timesTheProduceUntilTheBrokerAcknowledges() {
        CompletableFuture<SendResult<String, ChatMessageEvent>> send = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(send);

        producer.publish(chatEvent(), "corr-1", null);

        // Buffering the record locally must not count as a completed produce.
        assertThat(produceTimer().count()).isZero();

        send.complete(mock(SendResult.class));

        assertThat(produceTimer().count()).isEqualTo(1);
        assertThat(produceFailures()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsBrokerRejections() {
        CompletableFuture<SendResult<String, ChatMessageEvent>> send = new CompletableFuture<>();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(send);

        producer.publish(chatEvent(), null, null);
        send.completeExceptionally(new KafkaException("broker unavailable"));

        assertThat(produceTimer().count()).isEqualTo(1);
        assertThat(produceFailures()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsSendsThatFailBeforeReachingTheBuffer() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new IllegalStateException("serializer failed"));

        assertThatThrownBy(() -> producer.publish(chatEvent(), null, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(produceTimer().count()).isEqualTo(1);
        assertThat(produceFailures()).isEqualTo(1);
    }

    private Timer produceTimer() {
        return registry.get("streamsense_kafka_produce_latency_ms").timer();
    }

    private double produceFailures() {
        return registry.get("streamsense_kafka_produce_failures_total").counter().count();
    }

    private static ChatMessageEvent chatEvent() {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-1");
        event.setStreamer("streamer");
        event.setUser("viewer");
        event.setMessage("hello");
        event.setTimestamp(1710000000000L);
        return event;
    }
}
