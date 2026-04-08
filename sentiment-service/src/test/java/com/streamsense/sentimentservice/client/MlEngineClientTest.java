package com.streamsense.sentimentservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MlEngineClientTest {

    @Test
    void fallbackSentiment_returnsNeutralFallbackContract() {
        SentimentMetrics metrics = new SentimentMetrics(new SimpleMeterRegistry());
        MlEngineClient client = new MlEngineClient(new RestTemplate(), new StreamSenseProperties(), metrics);

        MlSentimentRequest request = new MlSentimentRequest("evt-1", "test", "u1", "hello", 1710000000000L);

        MlSentimentResponse response = client.fallbackSentiment(request, new MlDependencyException("ml down", null));

        assertThat(response.getLabel()).isEqualTo("NEUTRAL");
        assertThat(response.getScore()).isEqualTo(0.0d);
        assertThat(response.getModelVersion()).isEqualTo("fallback");
    }
}
