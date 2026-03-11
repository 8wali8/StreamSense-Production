package com.streamsense.apigateway.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.graphql.test.tester.HttpGraphQlTester;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GraphqlHealthQueryTest {

    @LocalServerPort
    int port;

    @Test
    void healthQuery_returnsOk() {
        HttpGraphQlTester tester = HttpGraphQlTester.builder(
                WebTestClient.bindToServer()
                        .baseUrl("http://localhost:" + port + "/graphql"))
                .build();

        tester.document("""
                query {
                  health
                }
                """)
                .execute()
                .path("health")
                .entity(String.class)
                .isEqualTo("ok");
    }
}