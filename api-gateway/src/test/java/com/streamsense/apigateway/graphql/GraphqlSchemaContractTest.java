package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events",
            "streamsense.topics.sponsorDetections=stream.sponsor.detections",
            "streamsense.services.sentiment-service.base-url=http://localhost:8083",
            "streamsense.services.video-service.base-url=http://localhost:8084",
            "streamsense.services.analytics-service.base-url=http://localhost:8085",
            "spring.kafka.bootstrap-servers=localhost:9092",
            "spring.kafka.consumer.group-id=api-gateway-test-group"
        })
class GraphqlSchemaContractTest {

    @Autowired
    private HttpGraphQlTester graphQlTester;

    @Test
    void queryRootExposesExpectedFields() {
        List<String> fields = graphQlTester
                .document(
                        """
                query {
                  __type(name: "Query") {
                    fields {
                      name
                    }
                  }
                }
                """)
                .execute()
                .path("__type.fields[*].name")
                .entityList(String.class)
                .get();

        assertThat(fields)
                .containsExactlyInAnyOrder(
                        "health",
                        "recentSentiment",
                        "recentSponsorSentiment",
                        "recentTranscriptSegments",
                        "recentTranscriptSentiment",
                        "recentSponsorTranscriptSentiment",
                        "sponsorDetections",
                        "streamMetricsSummary",
                        "streamMetricsTimeseries",
                        "sponsorExposureMetrics",
                        "brandSafetyMetrics",
                        "recommendations");
    }

    @Test
    void subscriptionRootExposesExpectedFields() {
        List<String> fields = graphQlTester
                .document(
                        """
                query {
                  __type(name: "Subscription") {
                    fields {
                      name
                    }
                  }
                }
                """)
                .execute()
                .path("__type.fields[*].name")
                .entityList(String.class)
                .get();

        assertThat(fields)
                .containsExactlyInAnyOrder(
                        "onChatMessage",
                        "onSentiment",
                        "onSponsorSentiment",
                        "onTranscriptSegment",
                        "onTranscriptSentiment",
                        "onSponsorTranscriptSentiment",
                        "onSponsorDetection");
    }
}
