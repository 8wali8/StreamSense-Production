package com.streamsense.sentimentservice.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;

@Component
public class MlEngineClient {

    private static final Logger log = LoggerFactory.getLogger(MlEngineClient.class);

    private final RestTemplate restTemplate;
    private final StreamSenseProperties properties;

    public MlEngineClient(RestTemplate restTemplate, StreamSenseProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public MlSentimentResponse analyzeSentiment(MlSentimentRequest request) {
        String url = properties.getMl().getBaseUrl() + "/ml/sentiment";

        log.info("calling ml-engine sentiment endpoint eventId={} streamer={}",
                request.getEventId(), request.getStreamer());

        MlSentimentResponse response;
        try {
            response = restTemplate.postForObject(url, request, MlSentimentResponse.class);
        } catch (RestClientException e) {
            log.error("ml-engine call failed eventId={} streamer={} error={}",
                    request.getEventId(), request.getStreamer(), e.getMessage(), e);
            throw e;
        }

        validateResponse(request, response);

        log.info("ml-engine response received eventId={} label={} score={}",
                request.getEventId(), response.getLabel(), response.getScore());

        return response;
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
