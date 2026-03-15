package com.streamsense.chatservice.consumer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.streamsense.chatservice.client.MlEngineClient;
import com.streamsense.chatservice.dto.MlSentimentRequest;
import com.streamsense.chatservice.dto.MlSentimentResponse;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.events.SentimentAnalysisEvent;
import com.streamsense.chatservice.kafka.SentimentKafkaProducer;

@Component
public class ChatMessageLogConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageLogConsumer.class);

    private final MlEngineClient mlEngineClient;
    private final SentimentKafkaProducer sentimentKafkaProducer;

    public ChatMessageLogConsumer(
            MlEngineClient mlEngineClient,
            SentimentKafkaProducer sentimentKafkaProducer) {
        this.mlEngineClient = mlEngineClient;
        this.sentimentKafkaProducer = sentimentKafkaProducer;
    }

    @KafkaListener(topics = "${streamsense.topics.chatMessages}", groupId = "${spring.kafka.consumer.group-id:chat-service-log}")
    public void onMessage(
            ChatMessageEvent event,
            ConsumerRecord<String, ChatMessageEvent> record) {
        String correlationId = headerAsString(record.headers().lastHeader("correlationId"));
        String traceparent = headerAsString(record.headers().lastHeader("traceparent"));

        log.info(
                "consumed eventId={} streamer={} correlationId={} traceparent={} message={}",
                event.getEventId(),
                event.getStreamer(),
                correlationId,
                traceparent,
                event.getMessage());

        try {
            MlSentimentRequest mlRequest = new MlSentimentRequest(
                    event.getEventId(),
                    event.getStreamer(),
                    event.getUser(),
                    event.getMessage(),
                    event.getTimestamp());

            MlSentimentResponse mlResponse = mlEngineClient.analyzeSentiment(mlRequest);

            SentimentAnalysisEvent sentimentEvent = new SentimentAnalysisEvent(
                    UUID.randomUUID().toString(),
                    event.getEventId(),
                    event.getStreamer(),
                    event.getUser(),
                    event.getMessage(),
                    event.getTimestamp(),
                    System.currentTimeMillis(),
                    mlResponse.getLabel(),
                    mlResponse.getScore(),
                    mlResponse.getModelVersion());

            sentimentKafkaProducer.publish(sentimentEvent, correlationId, traceparent);

            log.info(
                    "published sentimentEventId={} sourceEventId={} streamer={} label={} score={}",
                    sentimentEvent.getSentimentEventId(),
                    sentimentEvent.getSourceEventId(),
                    sentimentEvent.getStreamer(),
                    sentimentEvent.getLabel(),
                    sentimentEvent.getScore());

        } catch (Exception e) {
            log.error(
                    "failed sentiment processing eventId={} streamer={} error={}",
                    event.getEventId(),
                    event.getStreamer(),
                    e.getMessage(),
                    e);
        }
    }

    private String headerAsString(Header header) {
        if (header == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}