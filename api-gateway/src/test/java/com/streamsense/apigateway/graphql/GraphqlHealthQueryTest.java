package com.streamsense.apigateway.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

// need to add health check to graphQL and change test to update

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "streamsense.topics.chatMessages=stream.chat.messages",
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.kafka.consumer.group-id=api-gateway-test-group"
})
class GraphqlHealthQueryTest {

        @Autowired
        private HttpGraphQlTester graphQlTester;

        @Test
        void healthQuery_returnsGraphQlErrorBecauseNoResolverExists() {
                graphQlTester.document("""
                                query {
                                  health
                                }
                                """)
                                .execute()
                                .errors()
                                .satisfy(errors -> org.assertj.core.api.Assertions.assertThat(errors).isNotEmpty());
        }
}