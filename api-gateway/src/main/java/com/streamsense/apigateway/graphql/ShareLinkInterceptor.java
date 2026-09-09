package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.Deal;
import com.streamsense.apigateway.client.AnalyticsServiceClient;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.parser.Parser;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Read-only share mode. A request carrying {@value #HEADER} instead of a bearer token may run only
 * the report queries ({@link #ALLOWED}) and only for the deal the token opens; the resolved deal is
 * put on the GraphQL context as {@value #CONTEXT_KEY} and the controllers narrow their results to
 * it and strip the fee. Anything else answers with a GraphQL error carrying a stable code.
 */
@Component
public class ShareLinkInterceptor implements WebGraphQlInterceptor {

    public static final String HEADER = "X-StreamSense-Share";
    public static final String CONTEXT_KEY = "streamsense.shareDeal";
    static final Set<String> ALLOWED = Set.of("deal", "dealSummary", "sessionSummary", "sponsorMoments", "__typename");

    private static final Logger log = LoggerFactory.getLogger(ShareLinkInterceptor.class);

    private final AnalyticsServiceClient analytics;

    public ShareLinkInterceptor(AnalyticsServiceClient analytics) {
        this.analytics = analytics;
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        String token = request.getHeaders().getFirst(HEADER);
        if (token == null || token.isBlank()) {
            return chain.next(request);
        }
        if (!onlyAllowedQueries(request.getDocument())) {
            return Mono.just(errorResponse(
                    request, ErrorType.FORBIDDEN, "SHARE_FORBIDDEN", "This share link does not open that query"));
        }
        return analytics
                .shareDeal(token.trim())
                .map(deal -> {
                    request.configureExecutionInput(
                            (input, builder) -> builder.graphQLContext(context -> context.put(CONTEXT_KEY, deal))
                                    .build());
                    return deal;
                })
                .flatMap(deal -> chain.next(request))
                .switchIfEmpty(Mono.defer(() -> Mono.just(errorResponse(
                        request, ErrorType.UNAUTHORIZED, "SHARE_TOKEN_INVALID", "This share link is not valid"))))
                .onErrorResume(ex -> {
                    log.warn("share token could not be resolved: {}", ex.getMessage());
                    return Mono.just(errorResponse(
                            request,
                            ErrorType.INTERNAL_ERROR,
                            "SHARE_UNAVAILABLE",
                            "The share link could not be checked"));
                });
    }

    /** Every operation is a query whose top-level fields are all report fields. */
    static boolean onlyAllowedQueries(String documentText) {
        Document document;
        try {
            document = Parser.parse(documentText);
        } catch (RuntimeException ex) {
            // A malformed document is rejected by the engine with a syntax error; nothing to allow here.
            return true;
        }
        for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
            if (operation.getOperation() != OperationDefinition.Operation.QUERY) {
                return false;
            }
            for (Selection<?> selection : operation.getSelectionSet().getSelections()) {
                if (!(selection instanceof Field field) || !ALLOWED.contains(field.getName())) {
                    return false;
                }
            }
        }
        return true;
    }

    private static WebGraphQlResponse errorResponse(
            WebGraphQlRequest request, ErrorType type, String code, String message) {
        GraphQLError error = GraphqlErrorBuilder.newError()
                .errorType(type)
                .message(message)
                .extensions(Map.of("code", code))
                .build();
        ExecutionResult result =
                ExecutionResult.newExecutionResult().addError(error).build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result));
    }

    /** The shared deal on the context, or null when the request is not a share request. */
    static Deal sharedDeal(graphql.GraphQLContext context) {
        return context == null ? null : context.get(CONTEXT_KEY);
    }
}
