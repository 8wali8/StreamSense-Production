package com.streamsense.videoservice.kafka;

import com.streamsense.videoservice.events.FrameData;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class VideoFrameProducer {

    private final KafkaTemplate<String, FrameData> kafkaTemplate;
    private final String topic;

    public VideoFrameProducer(
            KafkaTemplate<String, FrameData> kafkaTemplate, @Value("${streamsense.topics.videoFrames}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(FrameData frame, String correlationId, String traceparent) {
        ProducerRecord<String, FrameData> record = new ProducerRecord<>(topic, frame.getStreamer(), frame);

        if (correlationId != null && !correlationId.isBlank()) {
            record.headers().add("correlationId", correlationId.getBytes(StandardCharsets.UTF_8));
        }

        if (traceparent != null && !traceparent.isBlank()) {
            record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));
        }

        kafkaTemplate.send(record);
    }
}
