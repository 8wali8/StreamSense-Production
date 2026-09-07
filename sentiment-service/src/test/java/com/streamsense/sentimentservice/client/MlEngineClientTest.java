package com.streamsense.sentimentservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.sentimentservice.config.StreamSenseProperties;
import com.streamsense.sentimentservice.dto.MlRelevanceRequest;
import com.streamsense.sentimentservice.dto.MlRelevanceResponse;
import com.streamsense.sentimentservice.dto.MlSentimentRequest;
import com.streamsense.sentimentservice.dto.MlSentimentResponse;
import com.streamsense.sentimentservice.metrics.SentimentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

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

    @Test
    void relevanceFallback_returnsNotRelevantContractWhenEndpointFails() {
        SentimentMetrics metrics = new SentimentMetrics(new SimpleMeterRegistry());
        StreamSenseProperties properties = new StreamSenseProperties();
        properties.getMl().setBaseUrl("http://localhost:1");
        MlEngineClient client = new MlEngineClient(new RestTemplate(), properties, metrics);

        MlRelevanceResponse response = client.analyzeRelevance(new MlRelevanceRequest(
                "evt-1", "test", "hello", "Nike", java.util.List.of(), java.util.List.of("shoes"), 0.5d));

        assertThat(response.isSponsorRelevant()).isFalse();
        assertThat(response.getRelevanceScore()).isEqualTo(0.0d);
        assertThat(response.getModelVersion()).isEqualTo("fallback");
    }
}
