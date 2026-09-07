package com.streamsense.sentimentservice.service;

import com.streamsense.sentimentservice.cache.RecentSentimentCache;
import com.streamsense.sentimentservice.client.MlEngineClient;
import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.MlRelevanceRequest;
import com.streamsense.sentimentservice.dto.MlRelevanceResponse;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;
import com.streamsense.sentimentservice.dto.SponsorRelevanceProfile;
import com.streamsense.sentimentservice.events.ChatMessageEvent;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.events.TranscriptSegmentEvent;
import com.streamsense.sentimentservice.events.TranscriptSentimentEvent;
import com.streamsense.sentimentservice.kafka.SentimentKafkaProducer;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;
import com.streamsense.sentimentservice.persistence.SentimentRecordEntity;
import com.streamsense.sentimentservice.persistence.SentimentRecordRepository;
import com.streamsense.sentimentservice.persistence.TranscriptSegmentRecordEntity;
import com.streamsense.sentimentservice.persistence.TranscriptSegmentRecordRepository;
import com.streamsense.sentimentservice.persistence.TranscriptSentimentRecordEntity;
import com.streamsense.sentimentservice.persistence.TranscriptSentimentRecordRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SentimentService {

    private static final Logger log = LoggerFactory.getLogger(SentimentService.class);

    private final MlEngineClient mlEngineClient;
    private final SentimentRecordRepository repository;
    private final TranscriptSegmentRecordRepository transcriptSegmentRepository;
    private final TranscriptSentimentRecordRepository transcriptSentimentRepository;
    private final SentimentKafkaProducer sentimentKafkaProducer;
    private final SentimentMetrics sentimentMetrics;
    private final StreamSenseProperties properties;
    private final RecentSentimentCache recentSentimentCache;
    private final SponsorRelevanceProfileService sponsorRelevanceProfileService;

    public SentimentService(
            MlEngineClient mlEngineClient,
            SentimentRecordRepository repository,
            TranscriptSegmentRecordRepository transcriptSegmentRepository,
            TranscriptSentimentRecordRepository transcriptSentimentRepository,
            SentimentKafkaProducer sentimentKafkaProducer,
            SentimentMetrics sentimentMetrics,
            StreamSenseProperties properties,
            RecentSentimentCache recentSentimentCache,
            SponsorRelevanceProfileService sponsorRelevanceProfileService) {
        this.mlEngineClient = mlEngineClient;
        this.repository = repository;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.transcriptSentimentRepository = transcriptSentimentRepository;
        this.sentimentKafkaProducer = sentimentKafkaProducer;
        this.sentimentMetrics = sentimentMetrics;
        this.properties = properties;
        this.recentSentimentCache = recentSentimentCache;
        this.sponsorRelevanceProfileService = sponsorRelevanceProfileService;
    }

    @Transactional
    public SentimentAnalysisEvent processChatMessage(ChatMessageEvent event, String correlationId, String traceparent) {
        log.info(
                "processing chat event sourceEventId={} streamer={} user={}",
                event.getEventId(),
                event.getStreamer(),
                event.getUser());

        MlSentimentRequest request = new MlSentimentRequest(
                event.getEventId(), event.getStreamer(), event.getUser(), event.getMessage(), event.getTimestamp());

        MlSentimentResponse response = sentimentMetrics.recordMlLatency(() -> mlEngineClient.analyzeSentiment(request));

        SentimentAnalysisEvent sentimentEvent = buildSentimentEvent(event, response);
        applyChatSponsorRelevance(sentimentEvent);

        if ("fallback".equalsIgnoreCase(sentimentEvent.getModelVersion())) {
            log.warn(
                    "persisting fallback sentiment sourceEventId={} streamer={} label={} score={}",
                    sentimentEvent.getSourceEventId(),
                    sentimentEvent.getStreamer(),
                    sentimentEvent.getLabel(),
                    sentimentEvent.getScore());
        }

        try {
            sentimentMetrics.recordPersistenceLatency(
                    () -> repository.save(SentimentRecordEntity.fromEvent(sentimentEvent)));
            sentimentMetrics.incrementPersistence("success");
            recentSentimentCache.evict(sentimentEvent.getStreamer());
        } catch (RuntimeException e) {
            sentimentMetrics.incrementPersistence("failure");
            log.error(
                    "failed to persist sentiment event sentimentEventId={} sourceEventId={} error={}",
                    sentimentEvent.getSentimentEventId(),
                    sentimentEvent.getSourceEventId(),
                    e.getMessage(),
                    e);
            throw e;
        }

        sentimentKafkaProducer.publish(sentimentEvent, correlationId, traceparent);
        sentimentMetrics.incrementProcessed(sentimentEvent.getLabel());
        sentimentMetrics.recordEndToEndLatency(System.currentTimeMillis() - event.getTimestamp());

        log.info(
                "processed sentimentEventId={} sourceEventId={} streamer={} label={} score={}",
                sentimentEvent.getSentimentEventId(),
                sentimentEvent.getSourceEventId(),
                sentimentEvent.getStreamer(),
                sentimentEvent.getLabel(),
                sentimentEvent.getScore());

        return sentimentEvent;
    }

    @Transactional
    public TranscriptSentimentEvent processTranscriptSegment(
            TranscriptSegmentEvent event, String correlationId, String traceparent) {
        if (event.getText() == null || event.getText().isBlank()) {
            throw new IllegalArgumentException("transcript segment text is blank");
        }

        String transcriptText = event.getText().trim();
        log.info(
                "processing transcript segment segmentId={} streamer={} textLength={}",
                event.getSegmentId(),
                event.getStreamer(),
                transcriptText.length());

        MlSentimentRequest request = new MlSentimentRequest(
                event.getSegmentId(), event.getStreamer(), "streamer-transcript", transcriptText, event.getEndedAt());

        MlSentimentResponse response = sentimentMetrics.recordMlLatency(() -> mlEngineClient.analyzeSentiment(request));

        TranscriptSegmentEvent normalizedSegment = normalizeTranscriptSegment(event, transcriptText);
        TranscriptSentimentEvent sentimentEvent = buildTranscriptSentimentEvent(normalizedSegment, response);
        applyTranscriptSponsorRelevance(sentimentEvent);

        try {
            sentimentMetrics.recordPersistenceLatency(
                    () -> transcriptSegmentRepository.save(TranscriptSegmentRecordEntity.fromEvent(normalizedSegment)));
            sentimentMetrics.recordPersistenceLatency(() ->
                    transcriptSentimentRepository.save(TranscriptSentimentRecordEntity.fromEvent(sentimentEvent)));
            sentimentMetrics.incrementPersistence("success");
        } catch (RuntimeException e) {
            sentimentMetrics.incrementPersistence("failure");
            log.error(
                    "failed to persist transcript sentiment segmentId={} sentimentEventId={} error={}",
                    event.getSegmentId(),
                    sentimentEvent.getSentimentEventId(),
                    e.getMessage(),
                    e);
            throw e;
        }

        sentimentKafkaProducer.publishTranscript(sentimentEvent, correlationId, traceparent);
        sentimentMetrics.incrementProcessed(sentimentEvent.getLabel());
        sentimentMetrics.recordEndToEndLatency(System.currentTimeMillis() - event.getEndedAt());

        log.info(
                "processed transcript sentimentEventId={} segmentId={} streamer={} label={} score={}",
                sentimentEvent.getSentimentEventId(),
                sentimentEvent.getSegmentId(),
                sentimentEvent.getStreamer(),
                sentimentEvent.getLabel(),
                sentimentEvent.getScore());

        return sentimentEvent;
    }

    @Transactional(readOnly = true)
    public List<SentimentAnalysisEvent> getRecentSentiment(String streamer, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        return recentSentimentCache
                .find(streamer, limit)
                .orElseGet(() -> loadRecentSentimentFromDatabase(streamer, limit));
    }

    @Transactional(readOnly = true)
    public List<TranscriptSegmentEvent> getRecentTranscriptSegments(String streamer, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        return transcriptSegmentRepository.findByStreamerOrderByEndedAtDesc(streamer, PageRequest.of(0, limit)).stream()
                .map(TranscriptSegmentRecordEntity::toEvent)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TranscriptSentimentEvent> getRecentTranscriptSentiment(String streamer, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        return transcriptSentimentRepository
                .findByStreamerOrderBySegmentEndedAtDesc(streamer, PageRequest.of(0, limit))
                .stream()
                .map(TranscriptSentimentRecordEntity::toEvent)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SentimentAnalysisEvent> getRecentSponsorSentiment(String streamer, String sponsor, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        var pageable = PageRequest.of(0, limit);
        var records = sponsor == null || sponsor.isBlank()
                ? repository.findByStreamerAndSponsorRelevantTrueOrderByChatTimestampDesc(streamer, pageable)
                : repository.findByStreamerAndSponsorRelevantTrueAndMatchedSponsorIgnoreCaseOrderByChatTimestampDesc(
                        streamer, sponsor.trim(), pageable);
        return records.stream().map(SentimentRecordEntity::toEvent).toList();
    }

    @Transactional(readOnly = true)
    public List<TranscriptSentimentEvent> getRecentSponsorTranscriptSentiment(
            String streamer, String sponsor, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        var pageable = PageRequest.of(0, limit);
        var records = sponsor == null || sponsor.isBlank()
                ? transcriptSentimentRepository.findByStreamerAndSponsorRelevantTrueOrderBySegmentEndedAtDesc(
                        streamer, pageable)
                : transcriptSentimentRepository
                        .findByStreamerAndSponsorRelevantTrueAndMatchedSponsorIgnoreCaseOrderBySegmentEndedAtDesc(
                                streamer, sponsor.trim(), pageable);
        return records.stream().map(TranscriptSentimentRecordEntity::toEvent).toList();
    }

    static SentimentAnalysisEvent buildSentimentEvent(ChatMessageEvent event, MlSentimentResponse response) {
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
        sentimentEvent.setSource(event.getSource());
        sentimentEvent.setChannelLogin(event.getChannelLogin());
        sentimentEvent.setStreamSessionId(event.getStreamSessionId());
        sentimentEvent.setTwitchStreamId(event.getTwitchStreamId());
        return sentimentEvent;
    }

    private TranscriptSegmentEvent normalizeTranscriptSegment(TranscriptSegmentEvent event, String transcriptText) {
        TranscriptSegmentEvent normalized = new TranscriptSegmentEvent();
        normalized.setSegmentId(event.getSegmentId());
        normalized.setStreamer(event.getStreamer());
        normalized.setText(transcriptText);
        normalized.setStartedAt(event.getStartedAt());
        normalized.setEndedAt(event.getEndedAt());
        normalized.setLanguage(event.getLanguage());
        normalized.setConfidence(event.getConfidence());
        normalized.setModelVersion(event.getModelVersion());
        normalized.setSource(event.getSource());
        normalized.setChannelLogin(event.getChannelLogin());
        normalized.setStreamSessionId(event.getStreamSessionId());
        normalized.setTwitchStreamId(event.getTwitchStreamId());
        normalized.setVideoTimestampMs(event.getVideoTimestampMs());
        normalized.setTranscriptSequence(event.getTranscriptSequence());
        normalized.setCaptureWorkerId(event.getCaptureWorkerId());
        return normalized;
    }

    private TranscriptSentimentEvent buildTranscriptSentimentEvent(
            TranscriptSegmentEvent event, MlSentimentResponse response) {
        TranscriptSentimentEvent sentimentEvent = new TranscriptSentimentEvent();
        sentimentEvent.setSentimentEventId(UUID.randomUUID().toString());
        sentimentEvent.setSegmentId(event.getSegmentId());
        sentimentEvent.setStreamer(event.getStreamer());
        sentimentEvent.setText(event.getText());
        sentimentEvent.setSegmentStartedAt(event.getStartedAt());
        sentimentEvent.setSegmentEndedAt(event.getEndedAt());
        sentimentEvent.setProcessedAt(System.currentTimeMillis());
        sentimentEvent.setLabel(response.getLabel());
        sentimentEvent.setScore(response.getScore());
        sentimentEvent.setModelVersion(response.getModelVersion());
        sentimentEvent.setTranscriptModelVersion(event.getModelVersion());
        sentimentEvent.setStreamSessionId(event.getStreamSessionId());
        sentimentEvent.setTranscriptSequence(event.getTranscriptSequence());
        return sentimentEvent;
    }

    private void applyChatSponsorRelevance(SentimentAnalysisEvent event) {
        sponsorRelevanceProfileService
                .findActive(event.getStreamer())
                .ifPresentOrElse(
                        profile -> applyRelevance(
                                event, event.getSentimentEventId(), event.getStreamer(), event.getMessage(), profile),
                        () -> markNotRelevant(event, "no-active-sponsor", "none"));
    }

    private void applyTranscriptSponsorRelevance(TranscriptSentimentEvent event) {
        sponsorRelevanceProfileService
                .findActive(event.getStreamer())
                .ifPresentOrElse(
                        profile -> applyRelevance(
                                event, event.getSentimentEventId(), event.getStreamer(), event.getText(), profile),
                        () -> markNotRelevant(event, "no-active-sponsor", "none"));
    }

    private void applyRelevance(
            SentimentAnalysisEvent event,
            String eventId,
            String streamer,
            String text,
            SponsorRelevanceProfile profile) {
        MlRelevanceResponse relevance = mlEngineClient.analyzeRelevance(new MlRelevanceRequest(
                eventId,
                streamer,
                text,
                profile.getSponsor(),
                profile.getAliases(),
                profile.getSemanticTerms(),
                profile.getMinScore()));
        event.setSponsorRelevant(relevance.isSponsorRelevant());
        event.setMatchedSponsor(relevance.getMatchedSponsor());
        event.setMatchedTerms(relevance.getMatchedTerms());
        event.setRelevanceScore(relevance.getRelevanceScore());
        event.setRelevanceReason(relevance.getRelevanceReason());
        event.setRelevanceVersion(relevance.getModelVersion());
    }

    private void applyRelevance(
            TranscriptSentimentEvent event,
            String eventId,
            String streamer,
            String text,
            SponsorRelevanceProfile profile) {
        MlRelevanceResponse relevance = mlEngineClient.analyzeRelevance(new MlRelevanceRequest(
                eventId,
                streamer,
                text,
                profile.getSponsor(),
                profile.getAliases(),
                profile.getSemanticTerms(),
                profile.getMinScore()));
        event.setSponsorRelevant(relevance.isSponsorRelevant());
        event.setMatchedSponsor(relevance.getMatchedSponsor());
        event.setMatchedTerms(relevance.getMatchedTerms());
        event.setRelevanceScore(relevance.getRelevanceScore());
        event.setRelevanceReason(relevance.getRelevanceReason());
        event.setRelevanceVersion(relevance.getModelVersion());
    }

    private void markNotRelevant(SentimentAnalysisEvent event, String reason, String version) {
        event.setSponsorRelevant(false);
        event.setMatchedTerms(List.of());
        event.setRelevanceScore(0.0d);
        event.setRelevanceReason(reason);
        event.setRelevanceVersion(version);
    }

    private void markNotRelevant(TranscriptSentimentEvent event, String reason, String version) {
        event.setSponsorRelevant(false);
        event.setMatchedTerms(List.of());
        event.setRelevanceScore(0.0d);
        event.setRelevanceReason(reason);
        event.setRelevanceVersion(version);
    }

    private List<SentimentAnalysisEvent> loadRecentSentimentFromDatabase(String streamer, int limit) {
        return sentimentMetrics.recordHistoryLookup("recentSentiment", "db", () -> {
            List<SentimentAnalysisEvent> recent =
                    repository.findByStreamerOrderByChatTimestampDesc(streamer, PageRequest.of(0, limit)).stream()
                            .map(SentimentRecordEntity::toEvent)
                            .toList();
            recentSentimentCache.put(streamer, limit, recent);
            log.info("sentiment history cache miss streamer={} limit={} results={}", streamer, limit, recent.size());
            return recent;
        });
    }
}
