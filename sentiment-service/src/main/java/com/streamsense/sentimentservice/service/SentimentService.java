package com.streamsense.sentimentservice.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.streamsense.sentimentservice.cache.RecentSentimentCache;
import com.streamsense.sentimentservice.client.MlEngineClient;
import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;
import com.streamsense.sentimentservice.events.ChatMessageEvent;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.kafka.SentimentKafkaProducer;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;
import com.streamsense.sentimentservice.persistence.SentimentRecordEntity;
import com.streamsense.sentimentservice.persistence.SentimentRecordRepository;

@Service
public class SentimentService {

    private static final Logger log = LoggerFactory.getLogger(SentimentService.class);

    private final MlEngineClient mlEngineClient;
    private final SentimentRecordRepository repository;
    private final SentimentKafkaProducer sentimentKafkaProducer;
    private final SentimentMetrics sentimentMetrics;
    private final StreamSenseProperties properties;
    private final RecentSentimentCache recentSentimentCache;

    public SentimentService(
            MlEngineClient mlEngineClient,
            SentimentRecordRepository repository,
            SentimentKafkaProducer sentimentKafkaProducer,
            SentimentMetrics sentimentMetrics,
            StreamSenseProperties properties,
            RecentSentimentCache recentSentimentCache) {
        this.mlEngineClient = mlEngineClient;
        this.repository = repository;
        this.sentimentKafkaProducer = sentimentKafkaProducer;
        this.sentimentMetrics = sentimentMetrics;
        this.properties = properties;
        this.recentSentimentCache = recentSentimentCache;
    }

    @Transactional
    public SentimentAnalysisEvent processChatMessage(ChatMessageEvent event, String correlationId, String traceparent) {
        log.info("processing chat event sourceEventId={} streamer={} user={}",
                event.getEventId(), event.getStreamer(), event.getUser());

        MlSentimentRequest request = new MlSentimentRequest(
                event.getEventId(),
                event.getStreamer(),
                event.getUser(),
                event.getMessage(),
                event.getTimestamp());

        MlSentimentResponse response = sentimentMetrics.recordMlLatency(
                () -> mlEngineClient.analyzeSentiment(request));

        SentimentAnalysisEvent sentimentEvent = buildSentimentEvent(event, response);

        if ("fallback".equalsIgnoreCase(sentimentEvent.getModelVersion())) {
            log.warn("persisting fallback sentiment sourceEventId={} streamer={} label={} score={}",
                    sentimentEvent.getSourceEventId(), sentimentEvent.getStreamer(), sentimentEvent.getLabel(),
                    sentimentEvent.getScore());
        }

        try {
            repository.save(SentimentRecordEntity.fromEvent(sentimentEvent));
            sentimentMetrics.incrementPersistence("success");
            recentSentimentCache.evict(sentimentEvent.getStreamer());
        } catch (RuntimeException e) {
            sentimentMetrics.incrementPersistence("failure");
            log.error("failed to persist sentiment event sentimentEventId={} sourceEventId={} error={}",
                    sentimentEvent.getSentimentEventId(), sentimentEvent.getSourceEventId(), e.getMessage(), e);
            throw e;
        }

        sentimentKafkaProducer.publish(sentimentEvent, correlationId, traceparent);
        sentimentMetrics.incrementProcessed(sentimentEvent.getLabel());

        log.info("processed sentimentEventId={} sourceEventId={} streamer={} label={} score={}",
                sentimentEvent.getSentimentEventId(), sentimentEvent.getSourceEventId(), sentimentEvent.getStreamer(),
                sentimentEvent.getLabel(), sentimentEvent.getScore());

        return sentimentEvent;
    }

    @Transactional(readOnly = true)
    public List<SentimentAnalysisEvent> getRecentSentiment(String streamer, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        return recentSentimentCache.find(streamer, limit)
                .orElseGet(() -> loadRecentSentimentFromDatabase(streamer, limit));
    }

    private SentimentAnalysisEvent buildSentimentEvent(ChatMessageEvent event, MlSentimentResponse response) {
        SentimentAnalysisEvent sentimentEvent = new SentimentAnalysisEvent();
        sentimentEvent.setSentimentEventId(UUID.randomUUID().toString());
        sentimentEvent.setSourceEventId(event.getEventId());
        sentimentEvent.setStreamer(event.getStreamer());
        sentimentEvent.setUser(event.getUser());
        sentimentEvent.setMessage(event.getMessage());
        sentimentEvent.setChatTimestamp(event.getTimestamp());
        sentimentEvent.setProcessedAt(System.currentTimeMillis());
        sentimentEvent.setLabel(response.getLabel());
        sentimentEvent.setScore(response.getScore());
        sentimentEvent.setModelVersion(response.getModelVersion());
        return sentimentEvent;
    }

    private List<SentimentAnalysisEvent> loadRecentSentimentFromDatabase(String streamer, int limit) {
        return sentimentMetrics.recordHistoryLookup("recentSentiment", "db", () -> {
            List<SentimentAnalysisEvent> recent = repository.findByStreamerOrderByChatTimestampDesc(streamer, PageRequest.of(0, limit))
                    .stream()
                    .map(SentimentRecordEntity::toEvent)
                    .toList();
            recentSentimentCache.put(streamer, limit, recent);
            log.info("sentiment history cache miss streamer={} limit={} results={}", streamer, limit, recent.size());
            return recent;
        });
    }
}
