package com.streamsense.chatservice.kafka;

import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.metrics.ChatMetrics;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class ChatKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(ChatKafkaProducer.class);

    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final String chatTopic;
    private final ChatMetrics chatMetrics;

    public ChatKafkaProducer(
            KafkaTemplate<String, ChatMessageEvent> kafkaTemplate,
            @Value("${streamsense.topics.chatMessages}") String chatTopic,
            ChatMetrics chatMetrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.chatTopic = chatTopic;
        this.chatMetrics = chatMetrics;
    }

    public void publish(ChatMessageEvent event, String correlationId, String traceparent) {
        ProducerRecord<String, ChatMessageEvent> record = new ProducerRecord<>(chatTopic, event.getStreamer(), event);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        // send() returns as soon as the record is buffered; the broker round-trip only completes the future,
        // so the timer is stopped there rather than when send() returns.
        Timer.Sample sample = chatMetrics.startKafkaProduce();
        CompletableFuture<SendResult<String, ChatMessageEvent>> send;
        try {
            send = kafkaTemplate.send(record);
        } catch (RuntimeException ex) {
            chatMetrics.recordKafkaProduce(sample, ex);
            throw ex;
        }
        send.whenComplete((result, failure) -> {
            chatMetrics.recordKafkaProduce(sample, failure);
            if (failure != null) {
                log.warn(
                        "chat message publish failed topic={} streamer={} eventId={}",
                        chatTopic,
                        event.getStreamer(),
                        event.getEventId(),
                        failure);
            }
        });
    }
}
