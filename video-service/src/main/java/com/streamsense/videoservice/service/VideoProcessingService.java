package com.streamsense.videoservice.service;

import com.streamsense.videoservice.cache.RecentSponsorDetectionsCache;
import com.streamsense.videoservice.client.MlEngineClient;
import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.dto.MlSponsorRequest;
import com.streamsense.videoservice.dto.MlSponsorResponse;
import com.streamsense.videoservice.events.FrameData;
import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.kafka.SponsorDetectionProducer;
import com.streamsense.videoservice.metrics.VideoMetrics;
import com.streamsense.videoservice.persistence.SponsorDetectionEntity;
import com.streamsense.videoservice.persistence.SponsorDetectionRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);

    private final MlEngineClient mlEngineClient;
    private final SponsorDetectionRepository repository;
    private final SponsorDetectionProducer sponsorDetectionProducer;
    private final VideoMetrics videoMetrics;
    private final StreamSenseProperties properties;
    private final RecentSponsorDetectionsCache recentSponsorDetectionsCache;

    public VideoProcessingService(
            MlEngineClient mlEngineClient,
            SponsorDetectionRepository repository,
            SponsorDetectionProducer sponsorDetectionProducer,
            VideoMetrics videoMetrics,
            StreamSenseProperties properties,
            RecentSponsorDetectionsCache recentSponsorDetectionsCache) {
        this.mlEngineClient = mlEngineClient;
        this.repository = repository;
        this.sponsorDetectionProducer = sponsorDetectionProducer;
        this.videoMetrics = videoMetrics;
        this.properties = properties;
        this.recentSponsorDetectionsCache = recentSponsorDetectionsCache;
    }

    @Transactional
    public SponsorDetectionEvent processFrame(FrameData frame, String correlationId, String traceparent) {
        log.info(
                "processing frame event frameId={} streamer={} sequence={}",
                frame.getFrameId(),
                frame.getStreamer(),
                frame.getFrameSequence());

        MlSponsorRequest request = new MlSponsorRequest(
                frame.getFrameId(),
                frame.getStreamer(),
                frame.getFrameRef(),
                frame.getFrameSequence(),
                frame.getCapturedAt(),
                frame.getSource(),
                frame.getChannelLogin(),
                frame.getStreamSessionId(),
                frame.getTwitchStreamId(),
                frame.getVideoTimestampMs(),
                frame.getArtifactContentType(),
                frame.getArtifactSizeBytes());

        MlSponsorResponse response = videoMetrics.recordInferenceLatency(() -> mlEngineClient.analyzeSponsor(request));

        SponsorDetectionEvent detectionEvent = buildDetectionEvent(frame, response);

        videoMetrics.recordPersistenceLatency(() -> repository.save(SponsorDetectionEntity.fromEvent(detectionEvent)));
        recentSponsorDetectionsCache.evict(detectionEvent.getStreamer());
        sponsorDetectionProducer.publish(detectionEvent, correlationId, traceparent);
        videoMetrics.incrementSponsorDetection(detectionEvent.getSponsor());
        videoMetrics.recordEndToEndLatency(System.currentTimeMillis() - frame.getCapturedAt());

        log.info(
                "processed sponsor detection detectionEventId={} frameId={} sponsor={} confidence={} modelVersion={}",
                detectionEvent.getDetectionEventId(),
                detectionEvent.getSourceFrameId(),
                detectionEvent.getSponsor(),
                detectionEvent.getConfidence(),
                detectionEvent.getModelVersion());

        return detectionEvent;
    }

    @Transactional(readOnly = true)
    public List<SponsorDetectionEvent> getRecentDetections(String streamer, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        return recentSponsorDetectionsCache
                .find(streamer, limit)
                .orElseGet(() -> loadRecentDetectionsFromDatabase(streamer, limit));
    }

    private SponsorDetectionEvent buildDetectionEvent(FrameData frame, MlSponsorResponse response) {
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId(UUID.randomUUID().toString());
        event.setSourceFrameId(frame.getFrameId());
        event.setStreamer(frame.getStreamer());
        event.setFrameRef(frame.getFrameRef());
        event.setFrameSequence(frame.getFrameSequence());
        event.setCapturedAt(frame.getCapturedAt());
        event.setProcessedAt(System.currentTimeMillis());
        event.setSponsor(response.getSponsor());
        event.setConfidence(response.getConfidence());
        event.setModelVersion(response.getModelVersion());
        event.setX(response.getX());
        event.setY(response.getY());
        event.setWidth(response.getWidth());
        event.setHeight(response.getHeight());
        event.setSource(frame.getSource());
        event.setChannelLogin(frame.getChannelLogin());
        event.setStreamSessionId(frame.getStreamSessionId());
        event.setTwitchStreamId(frame.getTwitchStreamId());
        event.setVideoTimestampMs(frame.getVideoTimestampMs());
        event.setFallback(SponsorDetectionEvent.isFallbackModelVersion(response.getModelVersion()));
        return event;
    }

    private List<SponsorDetectionEvent> loadRecentDetectionsFromDatabase(String streamer, int limit) {
        return videoMetrics.recordHistoryLookup("recentSponsorDetections", "db", () -> {
            List<SponsorDetectionEvent> recent =
                    repository.findByStreamerOrderByCapturedAtDesc(streamer, PageRequest.of(0, limit)).stream()
                            .map(SponsorDetectionEntity::toEvent)
                            .toList();
            recentSponsorDetectionsCache.put(streamer, limit, recent);
            log.info("sponsor history cache miss streamer={} limit={} results={}", streamer, limit, recent.size());
            return recent;
        });
    }
}
