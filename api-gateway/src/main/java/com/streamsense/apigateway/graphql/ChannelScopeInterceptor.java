package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.auth.AuthScope;
import com.streamsense.apigateway.config.GatewayWebSocketAuthInterceptor;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.language.Argument;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import java.util.Map;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * A streamer signed in with Twitch sees only their own channel. Every top-level field that takes a
 * {@code streamer} argument (queries and subscriptions alike) must name their login; a request for
 * another channel is answered with {@code CHANNEL_FORBIDDEN} before anything runs. Fields that take an
 * id instead (a deal, a session) check ownership in their resolver through the scope this puts on the
 * GraphQL context. Operators and access links pass untouched.
 */
@Component
public class ChannelScopeInterceptor implements WebGraphQlInterceptor {

    public static final String CODE = "CHANNEL_FORBIDDEN";

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        AuthScope scope = scopeOf(request);
        if (scope == null) {
            return chain.next(request);
        }
        request.configureExecutionInput(
                (input, builder) -> builder.graphQLContext(context -> context.put(AuthScope.GRAPHQL_CONTEXT_KEY, scope))
                        .build());
        if (!scope.isOperator() && asksForAnotherChannel(request.getDocument(), request.getVariables(), scope)) {
            return Mono.just(forbidden(request));
        }
        return chain.next(request);
    }

    /** The scope from the HTTP exchange, or from the WebSocket session the connection init established. */
    static AuthScope scopeOf(WebGraphQlRequest request) {
        Object fromExchange = request.getAttributes().get(AuthScope.ATTRIBUTE);
        if (fromExchange instanceof AuthScope scope) {
            return scope;
        }
        if (request instanceof WebSocketGraphQlRequest socket) {
            Object subject =
                    socket.getSessionInfo().getAttributes().get(GatewayWebSocketAuthInterceptor.SUBJECT_ATTRIBUTE);
            Object role = socket.getSessionInfo().getAttributes().get(GatewayWebSocketAuthInterceptor.ROLE_ATTRIBUTE);
            if (subject instanceof String login) {
                return new AuthScope(login, role instanceof String r ? r : null);
            }
        }
        return null;
    }

    /** The scope a resolver sees, or null for a request without one (auth off, or a share link). */
    public static AuthScope scope(graphql.GraphQLContext context) {
        return context == null ? null : context.get(AuthScope.GRAPHQL_CONTEXT_KEY);
    }

    static boolean asksForAnotherChannel(String documentText, Map<String, Object> variables, AuthScope scope) {
        Document document;
        try {
            document = Parser.parse(documentText);
        } catch (RuntimeException ex) {
            // The engine answers a malformed document with a syntax error; nothing to scope here.
            return false;
        }
        for (OperationDefinition operation : document.getDefinitionsOfType(OperationDefinition.class)) {
            for (Selection<?> selection : operation.getSelectionSet().getSelections()) {
                if (!(selection instanceof Field field)) {
                    continue;
                }
                for (Argument argument : field.getArguments()) {
                    if (!"streamer".equals(argument.getName())) {
                        continue;
                    }
                    String channel = channelValue(argument.getValue(), variables);
                    if (channel != null && !scope.allows(channel)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String channelValue(Value<?> value, Map<String, Object> variables) {
        if (value instanceof StringValue literal) {
            return literal.getValue();
        }
        if (value instanceof VariableReference reference) {
            Object bound = variables == null ? null : variables.get(reference.getName());
            return bound instanceof String s ? s : null;
        }
        return null;
    }

    private static WebGraphQlResponse forbidden(WebGraphQlRequest request) {
        GraphQLError error = GraphqlErrorBuilder.newError()
                .errorType(ErrorType.FORBIDDEN)
                .message("This channel is not yours to see")
                .extensions(Map.of("code", CODE))
                .build();
        ExecutionResult result =
                ExecutionResult.newExecutionResult().addError(error).build();
        return new WebGraphQlResponse(new DefaultExecutionGraphQlResponse(request.toExecutionInput(), result));
    }
}
