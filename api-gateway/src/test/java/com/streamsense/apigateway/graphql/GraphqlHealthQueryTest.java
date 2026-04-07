package com.streamsense.apigateway.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.HttpGraphQlTester;

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
        void healthQuery_returnsOkWithoutErrors() {
                graphQlTester.document("""
                                query {
                                  health
                                }
                                """)
                                .execute()
                                .errors()
                                .satisfy(errors -> assertThat(errors).isEmpty())
                                .path("health")
                                .entity(String.class)
                                .isEqualTo("ok");
        }

        @Test
        void namedHealthQuery_returnsOkWithoutErrors() {
                graphQlTester.document("""
                                query Health {
                                  health
                                }
                                """)
                                .execute()
                                .errors()
                                .satisfy(errors -> assertThat(errors).isEmpty())
                                .path("health")
                                .entity(String.class)
                                .isEqualTo("ok");
        }
}
