package com.streamsense.videoservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.dto.MlSponsorRequest;
import com.streamsense.videoservice.dto.MlSponsorResponse;
import com.streamsense.videoservice.metrics.VideoMetrics;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Component
public class MlEngineClient {

    private static final Logger log = LoggerFactory.getLogger(MlEngineClient.class);

    private final RestTemplate restTemplate;
    private final StreamSenseProperties properties;
    private final VideoMetrics videoMetrics;

    public MlEngineClient(RestTemplate restTemplate, StreamSenseProperties properties, VideoMetrics videoMetrics) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.videoMetrics = videoMetrics;
    }

    @Bulkhead(name = "mlSponsor", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "mlSponsor")
    @Retry(name = "mlSponsor", fallbackMethod = "fallbackSponsor")
    public MlSponsorResponse analyzeSponsor(MlSponsorRequest request) {
        String url = properties.getMl().getBaseUrl() + "/ml/sponsor";

        log.info("calling ml-engine sponsor endpoint frameId={} streamer={}",
                request.frameId(), request.streamer());

        MlSponsorResponse response;
        try {
            response = restTemplate.postForObject(url, request, MlSponsorResponse.class);
        } catch (RestClientException e) {
            log.error("ml-engine sponsor call failed frameId={} streamer={} error={}",
                    request.frameId(), request.streamer(), e.getMessage(), e);
            throw new MlDependencyException("ml-engine sponsor call failed for frameId=" + request.frameId(), e);
        }

        validateResponse(request, response);

        log.info("ml-engine sponsor response received frameId={} sponsor={} confidence={}",
                request.frameId(), response.getSponsor(), response.getConfidence());

        return response;
    }

    public MlSponsorResponse fallbackSponsor(MlSponsorRequest request, Throwable throwable) {
        if (!isFallbackCandidate(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("unexpected sponsor fallback path for frameId=" + request.frameId(), throwable);
        }

        videoMetrics.incrementSponsorFallback(throwable.getClass().getSimpleName());

        log.warn("using fallback sponsor detection frameId={} streamer={} reason={} message={}",
                request.frameId(), request.streamer(), throwable.getClass().getSimpleName(), throwable.getMessage());

        MlSponsorResponse fallback = new MlSponsorResponse();
        fallback.setSponsor("UNKNOWN");
        fallback.setConfidence(0.0d);
        fallback.setModelVersion("fallback");
        fallback.setX(0.0d);
        fallback.setY(0.0d);
        fallback.setWidth(0.0d);
        fallback.setHeight(0.0d);
        return fallback;
    }

    private boolean isFallbackCandidate(Throwable throwable) {
        return throwable instanceof MlDependencyException
                || throwable instanceof CallNotPermittedException
                || throwable instanceof BulkheadFullException;
    }

    private void validateResponse(MlSponsorRequest request, MlSponsorResponse response) {
        if (response == null) {
            throw new IllegalStateException("ml-engine returned null sponsor response for frameId=" + request.frameId());
        }

        if (!StringUtils.hasText(response.getSponsor())) {
            throw new IllegalStateException("ml-engine returned blank sponsor for frameId=" + request.frameId());
        }

        if (!StringUtils.hasText(response.getModelVersion())) {
            throw new IllegalStateException("ml-engine returned blank modelVersion for frameId=" + request.frameId());
        }

        requireRange("confidence", response.getConfidence(), request.frameId());
        requireRange("x", response.getX(), request.frameId());
        requireRange("y", response.getY(), request.frameId());
        requireRange("width", response.getWidth(), request.frameId());
        requireRange("height", response.getHeight(), request.frameId());

        if (response.getX() + response.getWidth() > 1.001d || response.getY() + response.getHeight() > 1.001d) {
            throw new IllegalStateException("ml-engine returned out-of-bounds sponsor box for frameId=" + request.frameId());
        }
    }

    private void requireRange(String field, double value, String frameId) {
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalStateException("ml-engine returned out-of-range " + field + " for frameId=" + frameId);
        }
    }
}
