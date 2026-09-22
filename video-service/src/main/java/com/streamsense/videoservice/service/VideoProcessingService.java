package com.streamsense.videoservice.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.streamsense.videoservice.cache.RecentSponsorDetectionsCache;
import com.streamsense.videoservice.client.AnalyticsDependencyException;
import com.streamsense.videoservice.client.CurrentDeal;
import com.streamsense.videoservice.client.CurrentDealClient;
import com.streamsense.videoservice.client.MlEngineClient;
import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.dto.MlSponsorRequest;
import com.streamsense.videoservice.dto.MlSponsorResponse;
import com.streamsense.videoservice.events.DetectionOutcome;
import com.streamsense.videoservice.events.FrameData;
import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.kafka.SponsorDetectionProducer;
import com.streamsense.videoservice.metrics.VideoMetrics;
import com.streamsense.videoservice.persistence.SponsorDetectionEntity;
import com.streamsense.videoservice.persistence.SponsorDetectionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A frame's journey: which deal the channel is in (analytics-service, cached), whether that deal has a
 * logo to look for, the detector's answer, and one detection event per frame whatever the answer was.
 * A channel without a logo is not sent to ml-engine at all ({@code NO_LOGO}); a channel whose deal
 * cannot be looked up, or whose frame ml-engine could not examine, gets {@code UNAVAILABLE}, never a
 * guess. Without an analytics-service base URL (local runs, the pipeline tests) every frame goes to
 * ml-engine as it always did, and the engine's own outcome is kept.
 */
@Service
public class VideoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(VideoProcessingService.class);
    static final String NO_LOGO_MODEL_VERSION = "no-logo";
    static final String UNKNOWN_SPONSOR = "UNKNOWN";

    private final MlEngineClient mlEngineClient;
    private final SponsorDetectionRepository repository;
    private final SponsorDetectionProducer sponsorDetectionProducer;
    private final VideoMetrics videoMetrics;
    private final StreamSenseProperties properties;
    private final RecentSponsorDetectionsCache recentSponsorDetectionsCache;
    private final ObjectProvider<CurrentDealClient> currentDeals;

    public VideoProcessingService(
            MlEngineClient mlEngineClient,
            SponsorDetectionRepository repository,
            SponsorDetectionProducer sponsorDetectionProducer,
            VideoMetrics videoMetrics,
            StreamSenseProperties properties,
            RecentSponsorDetectionsCache recentSponsorDetectionsCache,
            ObjectProvider<CurrentDealClient> currentDeals) {
        this.mlEngineClient = mlEngineClient;
        this.repository = repository;
        this.sponsorDetectionProducer = sponsorDetectionProducer;
        this.videoMetrics = videoMetrics;
        this.properties = properties;
        this.recentSponsorDetectionsCache = recentSponsorDetectionsCache;
        this.currentDeals = currentDeals;
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

        Examination examination = examine(frame, request);
        SponsorDetectionEvent detectionEvent = buildDetectionEvent(frame, examination);

        videoMetrics.recordPersistenceLatency(() -> repository.save(SponsorDetectionEntity.fromEvent(detectionEvent)));
        recentSponsorDetectionsCache.evict(detectionEvent.getStreamer());
        sponsorDetectionProducer.publish(detectionEvent, correlationId, traceparent);
        videoMetrics.incrementSponsorDetection(detectionEvent.getSponsor());
        videoMetrics.incrementOutcome(detectionEvent.getOutcome());
        videoMetrics.recordEndToEndLatency(System.currentTimeMillis() - frame.getCapturedAt());

        log.info(
                "processed sponsor detection detectionEventId={} frameId={} outcome={} sponsor={} confidence={} modelVersion={}",
                detectionEvent.getDetectionEventId(),
                detectionEvent.getSourceFrameId(),
                detectionEvent.getOutcome(),
                detectionEvent.getSponsor(),
                detectionEvent.getConfidence(),
                detectionEvent.getModelVersion());

        return detectionEvent;
    }

    /** The detector's answer for the frame, or the answer that stands in when it is not asked or cannot be. */
    private Examination examine(FrameData frame, MlSponsorRequest request) {
        CurrentDealClient client = currentDeals.getIfAvailable();
        if (client == null) {
            return fromEngine(request, null);
        }
        Optional<CurrentDeal> deal;
        try {
            deal = client.find(frame.getStreamer());
        } catch (AnalyticsDependencyException ex) {
            log.warn(
                    "current deal unknown for streamer={} frameId={}: {}",
                    frame.getStreamer(),
                    frame.getFrameId(),
                    ex.getMessage());
            videoMetrics.incrementSponsorFallback(ex.getClass().getSimpleName());
            return new Examination(
                    MlSponsorResponse.empty(
                            UNKNOWN_SPONSOR,
                            SponsorDetectionEvent.FALLBACK_MODEL_VERSION,
                            DetectionOutcome.UNAVAILABLE.name()),
                    DetectionOutcome.UNAVAILABLE,
                    null,
                    null);
        }
        if (deal.isEmpty() || !deal.get().hasLogo()) {
            // Nothing to look for: the frame is recorded as not examined, and ml-engine is not asked.
            String sponsor = deal.map(CurrentDeal::sponsor).orElse(UNKNOWN_SPONSOR);
            return new Examination(
                    MlSponsorResponse.empty(sponsor, NO_LOGO_MODEL_VERSION, DetectionOutcome.NO_LOGO.name()),
                    DetectionOutcome.NO_LOGO,
                    deal.map(CurrentDeal::id).orElse(null),
                    null);
        }
        CurrentDeal current = deal.get();
        List<String> refs = current.logos().stream().map(CurrentDeal.Logo::ref).toList();
        Long logoId = current.logos().get(0).id();
        return fromEngine(request.forDeal(current.sponsor(), current.id(), logoId, refs), current);
    }

    private Examination fromEngine(MlSponsorRequest request, CurrentDeal deal) {
        MlSponsorResponse response = videoMetrics.recordInferenceLatency(() -> mlEngineClient.analyzeSponsor(request));
        DetectionOutcome outcome = SponsorDetectionEvent.isFallbackModelVersion(response.getModelVersion())
                ? DetectionOutcome.UNAVAILABLE
                : DetectionOutcome.resolve(response.getOutcome(), response.getModelVersion());
        return new Examination(response, outcome, deal == null ? null : deal.id(), request.logoId());
    }

    @Transactional(readOnly = true)
    public List<SponsorDetectionEvent> getRecentDetections(String streamer, int requestedLimit) {
        int limit = Math.min(requestedLimit, properties.getHistory().getMaxLimit());
        return recentSponsorDetectionsCache
                .find(streamer, limit)
                .orElseGet(() -> loadRecentDetectionsFromDatabase(streamer, limit));
    }

    private SponsorDetectionEvent buildDetectionEvent(FrameData frame, Examination examination) {
        MlSponsorResponse response = examination.response();
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        // Derived from the frame, so a frame processed twice (a retried delivery, a resumed VOD import)
        // yields the same detection event and analytics counts it once.
        event.setDetectionEventId(UUID.nameUUIDFromBytes(("detection:" + frame.getFrameId()).getBytes(UTF_8))
                .toString());
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
        event.setOutcome(examination.outcome().name());
        event.setDealId(examination.dealId());
        event.setLogoId(examination.logoId());
        return event;
    }

    /** Detections between two epoch-millis instants, oldest first, for a session report. Not cached. */
    @Transactional(readOnly = true)
    public List<SponsorDetectionEvent> getDetectionsInRange(String streamer, long from, long to, int limit) {
        return repository
                .findByStreamerAndCapturedAtBetweenOrderByCapturedAtAsc(streamer, from, to, PageRequest.of(0, limit))
                .stream()
                .map(SponsorDetectionEntity::toEvent)
                .toList();
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

    /** What was found out about a frame: the detector's (or stand-in) answer and what it was made against. */
    private record Examination(MlSponsorResponse response, DetectionOutcome outcome, Long dealId, Long logoId) {}
}
