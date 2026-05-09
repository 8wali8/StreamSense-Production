package com.streamsense.sentimentservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.MlRelevanceRequest;
import com.streamsense.sentimentservice.dto.MlRelevanceResponse;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;

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
    private final SentimentMetrics sentimentMetrics;

    public MlEngineClient(RestTemplate restTemplate, StreamSenseProperties properties, SentimentMetrics sentimentMetrics) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.sentimentMetrics = sentimentMetrics;
    }

    @Bulkhead(name = "mlSentiment", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "mlSentiment")
    @Retry(name = "mlSentiment", fallbackMethod = "fallbackSentiment")
    public MlSentimentResponse analyzeSentiment(MlSentimentRequest request) {
        String url = properties.getMl().getBaseUrl() + "/ml/sentiment";

        log.info("calling ml-engine sentiment endpoint eventId={} streamer={}",
                request.getEventId(), request.getStreamer());

        MlSentimentResponse response;
        try {
            response = restTemplate.postForObject(url, request, MlSentimentResponse.class);
        } catch (RestClientException e) {
            sentimentMetrics.incrementProtectedCall("failure");
            log.error("ml-engine call failed eventId={} streamer={} error={}",
                    request.getEventId(), request.getStreamer(), e.getMessage(), e);
            throw new MlDependencyException("ml-engine call failed for eventId=" + request.getEventId(), e);
        } catch (RuntimeException e) {
            sentimentMetrics.incrementProtectedCall("failure");
            throw e;
        }

        validateResponse(request, response);
        sentimentMetrics.incrementProtectedCall("success");

        log.info("ml-engine response received eventId={} label={} score={}",
                request.getEventId(), response.getLabel(), response.getScore());

        return response;
    }

    public MlSentimentResponse fallbackSentiment(MlSentimentRequest request, Throwable throwable) {
        if (!isFallbackCandidate(throwable)) {
            if (throwable instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("unexpected fallback path for eventId=" + request.getEventId(), throwable);
        }

        sentimentMetrics.incrementProtectedCall("fallback");
        sentimentMetrics.incrementFallback(throwable.getClass().getSimpleName());

        log.warn("using fallback sentiment eventId={} streamer={} reason={} message={}",
                request.getEventId(), request.getStreamer(), throwable.getClass().getSimpleName(), throwable.getMessage());

        MlSentimentResponse fallback = new MlSentimentResponse();
        fallback.setLabel("NEUTRAL");
        fallback.setScore(0.0d);
        fallback.setModelVersion("fallback");
        return fallback;
    }

    public MlRelevanceResponse analyzeRelevance(MlRelevanceRequest request) {
        String url = properties.getMl().getBaseUrl() + "/ml/relevance";

        log.info("calling ml-engine relevance endpoint eventId={} streamer={} sponsor={}",
                request.getEventId(), request.getStreamer(), request.getSponsor());

        MlRelevanceResponse response;
        try {
            response = restTemplate.postForObject(url, request, MlRelevanceResponse.class);
        } catch (RestClientException e) {
            log.warn("ml-engine relevance call failed eventId={} streamer={} sponsor={} error={}",
                    request.getEventId(), request.getStreamer(), request.getSponsor(), e.getMessage());
            return MlRelevanceResponse.notRelevant("relevance-ml-fallback", "fallback");
        }

        if (response == null) {
            return MlRelevanceResponse.notRelevant("relevance-null-response", "fallback");
        }
        if (!StringUtils.hasText(response.getModelVersion())) {
            response.setModelVersion("unknown-relevance");
        }
        if (!StringUtils.hasText(response.getRelevanceReason())) {
            response.setRelevanceReason(response.isSponsorRelevant() ? "relevant" : "not-relevant");
        }
        if (response.getRelevanceScore() < 0.0d || response.getRelevanceScore() > 1.0d) {
            response.setRelevanceScore(Math.max(0.0d, Math.min(1.0d, response.getRelevanceScore())));
        }
        return response;
    }

    private boolean isFallbackCandidate(Throwable throwable) {
        return throwable instanceof MlDependencyException
                || throwable instanceof CallNotPermittedException
                || throwable instanceof BulkheadFullException;
    }

    private void validateResponse(MlSentimentRequest request, MlSentimentResponse response) {
        if (response == null) {
            throw new IllegalStateException("ml-engine returned null response for eventId=" + request.getEventId());
        }

        if (!StringUtils.hasText(response.getLabel())) {
            throw new IllegalStateException("ml-engine returned blank label for eventId=" + request.getEventId());
        }

        if (!StringUtils.hasText(response.getModelVersion())) {
            throw new IllegalStateException("ml-engine returned blank modelVersion for eventId=" + request.getEventId());
        }

        if (response.getScore() < -1.0 || response.getScore() > 1.0) {
            throw new IllegalStateException("ml-engine returned out-of-range score for eventId=" + request.getEventId());
        }
    }
}
