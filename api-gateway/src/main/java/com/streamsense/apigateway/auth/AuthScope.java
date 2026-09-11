package com.streamsense.apigateway.auth;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who a request is for, once the token has been checked: the login and the role. A streamer is
 * confined to their own channel; an operator, or a token minted without a role (an access link),
 * may look at any channel. Carried on the exchange for the filters, on the WebSocket session for
 * subscriptions, on the GraphQL context for resolvers, and to the services as two headers.
 */
public record AuthScope(String login, String role) {

    public static final String ATTRIBUTE = "streamsense.auth.scope";
    public static final String GRAPHQL_CONTEXT_KEY = "streamsense.auth.scope";
    public static final String LOGIN_HEADER = "X-StreamSense-Auth-Login";
    public static final String ROLE_HEADER = "X-StreamSense-Auth-Role";

    /** The channel a REST path names: {@code /api/…/streams/{channel}/…} or {@code …/sponsors/{channel}}. */
    private static final Pattern REST_CHANNEL =
            Pattern.compile("^/api/[a-z-]+/(?:streams|relevance/sponsors)/([^/?]+)(?:[/?].*)?$");

    public boolean isOperator() {
        return role == null || GatewayTokenIssuer.ROLE_OPERATOR.equals(role);
    }

    /** Whether this scope may read the given channel (a streamer only their own; anyone else, any). */
    public boolean allows(String channel) {
        return isOperator() || channel == null || normalize(channel).equals(normalize(login));
    }

    /** The channel a REST request is about, from its path or its {@code streamer} query parameter; null when none. */
    public static String restChannel(String path, String streamerParam) {
        Matcher matcher = REST_CHANNEL.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return streamerParam == null || streamerParam.isBlank() ? null : streamerParam;
    }

    /** Twitch logins compare case-insensitively; the console sometimes carries an {@code @}. */
    public static String normalize(String channel) {
        return channel == null ? "" : channel.trim().replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
    }

    /** Thrown by a resolver when a streamer asks about a channel that is not theirs. */
    public static final class ChannelForbiddenException extends RuntimeException {

        public ChannelForbiddenException() {
            super("This channel is not yours to see");
        }
    }
}
