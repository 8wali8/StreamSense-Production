package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.Deal;
import com.streamsense.apigateway.analytics.DealSummary;
import com.streamsense.apigateway.client.AnalyticsServiceClient;
import graphql.GraphQLContext;
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
    public Mono<Deal> deal(@Argument("id") String id, GraphQLContext context) {
        Long dealId = parseId(id);
        if (dealId == null) {
            return Mono.empty();
        }
        Deal shared = ShareLinkInterceptor.sharedDeal(context);
        if (shared != null) {
            return shared.id() == dealId ? Mono.just(shared.forSharedView()) : Mono.empty();
        }
        return analytics.deal(dealId);
    }

    @QueryMapping
    public Mono<DealSummary> dealSummary(@Argument("id") String id, GraphQLContext context) {
        Long dealId = parseId(id);
        if (dealId == null) {
            return Mono.empty();
        }
        Deal shared = ShareLinkInterceptor.sharedDeal(context);
        if (shared != null && shared.id() != dealId) {
            return Mono.empty();
        }
        Mono<DealSummary> summary = analytics.dealSummary(dealId);
        return shared == null
                ? summary
                : summary.map(s -> new DealSummary(s.deal().forSharedView(), s.totals(), s.sessions()));
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
