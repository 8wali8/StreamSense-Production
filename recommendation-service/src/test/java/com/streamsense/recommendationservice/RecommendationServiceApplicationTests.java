package com.streamsense.recommendationservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"})
class RecommendationServiceApplicationTests {

    @Test
    void contextLoads() {}
}
