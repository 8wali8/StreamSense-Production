package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.Deal;
import com.streamsense.apigateway.analytics.DealSummary;
import com.streamsense.apigateway.client.AnalyticsServiceClient;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

/** Deals, read from analytics-service; writes go through its REST route. */
@Controller
public class DealGraphqlController {

    private final AnalyticsServiceClient analytics;

    public DealGraphqlController(AnalyticsServiceClient analytics) {
        this.analytics = analytics;
    }

    @QueryMapping
    public Mono<List<Deal>> deals(@Argument("streamer") String streamer, @Argument("limit") Integer limit) {
        return analytics.deals(streamer, limit);
    }

    @QueryMapping
    public Mono<Deal> deal(@Argument("id") String id) {
        Long dealId = parseId(id);
        return dealId == null ? Mono.empty() : analytics.deal(dealId);
    }

    @QueryMapping
    public Mono<DealSummary> dealSummary(@Argument("id") String id) {
        Long dealId = parseId(id);
        return dealId == null ? Mono.empty() : analytics.dealSummary(dealId);
    }

    private static Long parseId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
