package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.client.RecommendationServiceClient;
import com.streamsense.apigateway.model.Recommendation;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Controller
public class RecommendationGraphqlController {

    private final RecommendationServiceClient recommendationServiceClient;

    public RecommendationGraphqlController(RecommendationServiceClient recommendationServiceClient) {
        this.recommendationServiceClient = recommendationServiceClient;
    }

    @QueryMapping
    public Mono<List<Recommendation>> recommendations(
            @Argument("streamer") String streamer, @Argument("limit") int limit) {
        return recommendationServiceClient.recommendations(streamer, limit);
    }
}
