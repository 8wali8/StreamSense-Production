package com.streamsense.apigateway.auth;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Who a request is for, once the token has been checked: the login and the role. A streamer is
 * confined to their own channel; an operator, or a token minted without a role (an access link),
 * may look at any channel; only an operator may steer the pipeline. Carried on the exchange for the
 * filters, on the WebSocket session for subscriptions, on the GraphQL context for resolvers, and to
 * the services as two headers.
 */
public record AuthScope(String login, String role) {

    public static final String ATTRIBUTE = "streamsense.auth.scope";
    public static final String GRAPHQL_CONTEXT_KEY = "streamsense.auth.scope";
    public static final String LOGIN_HEADER = "X-StreamSense-Auth-Login";
    public static final String ROLE_HEADER = "X-StreamSense-Auth-Role";

    /** The channel a REST path names: {@code /api/…/streams/{channel}/…} or {@code …/sponsors/{channel}}. */
    private static final Pattern REST_CHANNEL =
            Pattern.compile("^/api/[a-z-]+/(?:streams|relevance/sponsors)/([^/?]+)(?:[/?].*)?$");

    /** The channel a self-service capture path names: {@code /api/chat/twitch/channels/{channel}} and friends. */
    private static final Pattern CHANNEL_PATH =
            Pattern.compile("^/api/(?:chat/twitch|video/capture)/channels/([^/?]+)(?:[/?].*)?$");

    /** Whether this scope may steer the pipeline: a Twitch sign-in on the operators list, nothing else. */
    public boolean isOperator() {
        return GatewayTokenIssuer.ROLE_OPERATOR.equals(role);
    }

    /** Whether this scope is confined to its own channel: a token with a role that is not operator, a streamer. */
    public boolean isConfined() {
        return role != null && !isOperator();
    }

    /** Whether this scope may read the given channel (a streamer only their own; anyone else, any). */
    public boolean allows(String channel) {
        return !isConfined() || channel == null || normalize(channel).equals(normalize(login));
    }

    /** The channel a REST request is about, from its path or its {@code streamer} query parameter; null when none. */
    public static String restChannel(String path, String streamerParam) {
        Matcher matcher = REST_CHANNEL.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        String channel = channelInPath(path);
        if (channel != null) {
            return channel;
        }
        return streamerParam == null || streamerParam.isBlank() ? null : streamerParam;
    }

    /** The channel a self-service capture path names, or null when the path is not one of them. */
    public static String channelInPath(String path) {
        Matcher matcher = CHANNEL_PATH.matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
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
