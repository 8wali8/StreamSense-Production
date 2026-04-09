package com.streamsense.videoservice.controller;

import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.streamsense.videoservice.api.FrameUploadRequest;
import com.streamsense.videoservice.api.FrameUploadResponse;
import com.streamsense.videoservice.config.CorrelationIdFilter;
import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.events.FrameData;
import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.kafka.VideoFrameProducer;
import com.streamsense.videoservice.metrics.VideoMetrics;
import com.streamsense.videoservice.service.VideoProcessingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final VideoFrameProducer videoFrameProducer;
    private final VideoProcessingService videoProcessingService;
    private final VideoMetrics videoMetrics;
    private final StreamSenseProperties properties;

    public VideoController(
            VideoFrameProducer videoFrameProducer,
            VideoProcessingService videoProcessingService,
            VideoMetrics videoMetrics,
            StreamSenseProperties properties) {
        this.videoFrameProducer = videoFrameProducer;
        this.videoProcessingService = videoProcessingService;
        this.videoMetrics = videoMetrics;
        this.properties = properties;
    }

    @PostMapping("/upload-frame")
    public ResponseEntity<FrameUploadResponse> uploadFrame(
            @Valid @RequestBody FrameUploadRequest request,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_HEADER, required = false) String correlationId,
            @RequestHeader(value = CorrelationIdFilter.CORRELATION_ID_KEY, required = false) String legacyCorrelationId,
            @RequestHeader(value = "traceparent", required = false) String traceparent) {
        if (request.getFrameRef().length() > properties.getPayload().getMaxFrameRefLength()) {
            throw new IllegalArgumentException("frameRef exceeds configured maximum length");
        }

        String frameId = UUID.randomUUID().toString();

        FrameData frameData = new FrameData();
        frameData.setFrameId(frameId);
        frameData.setStreamer(request.getStreamer());
        frameData.setFrameRef(request.getFrameRef());
        frameData.setFrameSequence(request.getFrameSequence());
        frameData.setCapturedAt(request.getCapturedAt());

        videoFrameProducer.publish(frameData,
                firstNonBlank(correlationId, legacyCorrelationId, MDC.get(CorrelationIdFilter.CORRELATION_ID_KEY)),
                traceparent);
        videoMetrics.incrementFramesIngested();

        return ResponseEntity.accepted().body(new FrameUploadResponse(frameId, "accepted"));
    }

    @GetMapping("/detections/recent")
    public List<SponsorDetectionEvent> recentDetections(
            @RequestParam("streamer") @NotBlank String streamer,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        int requestedLimit = limit != null ? limit : properties.getHistory().getDefaultLimit();
        return videoProcessingService.getRecentDetections(streamer, requestedLimit);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
