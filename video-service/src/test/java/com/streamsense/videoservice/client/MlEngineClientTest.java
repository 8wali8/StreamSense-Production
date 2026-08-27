package com.streamsense.videoservice.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import com.streamsense.videoservice.config.StreamSenseProperties;
import com.streamsense.videoservice.dto.MlSponsorRequest;
import com.streamsense.videoservice.dto.MlSponsorResponse;
import com.streamsense.videoservice.metrics.VideoMetrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MlEngineClientTest {

    @Test
    void fallbackSponsor_returnsExpectedFallbackContract() {
        VideoMetrics metrics = new VideoMetrics(new SimpleMeterRegistry());
        MlEngineClient client = new MlEngineClient(new RestTemplate(), new StreamSenseProperties(), metrics);

        MlSponsorRequest request = new MlSponsorRequest("frame-1", "test", "frames/test.png", 1, 1710000000000L);

        MlSponsorResponse response = client.fallbackSponsor(request, new MlDependencyException("ml down", null));

        assertThat(response.getSponsor()).isEqualTo("UNKNOWN");
        assertThat(response.getConfidence()).isEqualTo(0.0d);
        assertThat(response.getModelVersion()).isEqualTo("fallback");
        assertThat(response.getX()).isEqualTo(0.0d);
        assertThat(response.getY()).isEqualTo(0.0d);
        assertThat(response.getWidth()).isEqualTo(0.0d);
        assertThat(response.getHeight()).isEqualTo(0.0d);
    }
}
