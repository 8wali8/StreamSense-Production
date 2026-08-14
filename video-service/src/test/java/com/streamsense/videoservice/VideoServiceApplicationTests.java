package com.streamsense.videoservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "streamsense.topics.videoFrames=stream.video.frames",
        "streamsense.topics.videoFramesDlt=stream.video.frames.dlt",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.ml.base-url=http://localhost:8000"
})
class VideoServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
