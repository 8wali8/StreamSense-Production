package com.streamsense.chatservice.client;

import com.streamsense.chatservice.config.StreamSenseProperties;
import com.streamsense.chatservice.dto.MlSentimentRequest;
import com.streamsense.chatservice.dto.MlSentimentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<MlSentimentRequest> entity = new HttpEntity<>(request, headers);

        log.info("Calling ml-engine sentiment endpoint for eventId={} streamer={}",
                request.getEventId(), request.getStreamer());

        MlSentimentResponse response = restTemplate.postForObject(
                url,
                entity,
                MlSentimentResponse.class);

        if (response == null) {
            throw new IllegalStateException("ml-engine returned null response");
        }

        log.info("ml-engine response received eventId={} label={} score={}",
                request.getEventId(), response.getLabel(), response.getScore());

        return response;
    }
}