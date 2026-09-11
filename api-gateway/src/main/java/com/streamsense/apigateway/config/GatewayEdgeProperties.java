package com.streamsense.apigateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

@ConfigurationProperties(prefix = "streamsense.gateway")
public class GatewayEdgeProperties {

    private final Auth auth = new Auth();
    private final List<RateLimitRule> rateLimits = new ArrayList<>();
    private boolean rateLimitEnabled = true;
    // Where the counters live: "redis" is shared by every replica, "memory" is per instance (local runs, tests).
    private String rateLimitStore = "memory";
    // When the store is unreachable, let requests through (true) or reject them (false).
    private boolean rateLimitFailOpen = true;
    // How many X-Forwarded-For entries were appended by proxies we operate; 0 means the header is never trusted.
    private int trustedProxyHops = 0;

    public Auth getAuth() {
        return auth;
    }

    public List<RateLimitRule> getRateLimits() {
        return rateLimits;
    }

    public boolean isRateLimitEnabled() {
        return rateLimitEnabled;
    }

    public void setRateLimitEnabled(boolean rateLimitEnabled) {
        this.rateLimitEnabled = rateLimitEnabled;
    }

    public String getRateLimitStore() {
        return rateLimitStore;
    }

    public void setRateLimitStore(String rateLimitStore) {
        this.rateLimitStore = rateLimitStore;
    }

    public boolean isRateLimitFailOpen() {
        return rateLimitFailOpen;
    }

    public void setRateLimitFailOpen(boolean rateLimitFailOpen) {
        this.rateLimitFailOpen = rateLimitFailOpen;
    }

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public static class Auth {

        private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

        private boolean enabled;
        private List<String> protectedPaths = List.of("/graphql", "/api/**");
        private List<String> excludedPaths = List.of("/actuator/**", "/graphiql", "/graphiql/**");
        private String requiredIssuer = "streamsense-local";
        private String requiredAudience = "streamsense-clients";
        // Shared HS256 key; required whenever auth is enabled (see GatewayAuthStartupCheck).
        private String hmacSecret;
        // Twitch logins that sign in as operators; everyone else who signs in with Twitch is a streamer.
        private List<String> operators = new ArrayList<>();
        // Paths where anything but a read steers the pipeline (channel switching, profiles, ingest, ML calls):
        // a token whose role is not operator is refused there. Tokens without a role keep their full access.
        private List<String> operatorOnlyPaths =
                List.of("/api/chat/**", "/api/video/**", "/api/sentiment/**", "/ml/**");
        private final Twitch twitch = new Twitch();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getProtectedPaths() {
            return protectedPaths;
        }

        public void setProtectedPaths(List<String> protectedPaths) {
            this.protectedPaths = protectedPaths;
        }

        public List<String> getExcludedPaths() {
            return excludedPaths;
        }

        public void setExcludedPaths(List<String> excludedPaths) {
            this.excludedPaths = excludedPaths;
        }

        public String getRequiredIssuer() {
            return requiredIssuer;
        }

        public void setRequiredIssuer(String requiredIssuer) {
            this.requiredIssuer = requiredIssuer;
        }

        public String getRequiredAudience() {
            return requiredAudience;
        }

        public void setRequiredAudience(String requiredAudience) {
            this.requiredAudience = requiredAudience;
        }

        public String getHmacSecret() {
            return hmacSecret;
        }

        public void setHmacSecret(String hmacSecret) {
            this.hmacSecret = hmacSecret;
        }

        public List<String> getOperators() {
            return operators;
        }

        public void setOperators(List<String> operators) {
            this.operators = operators;
        }

        public List<String> getOperatorOnlyPaths() {
            return operatorOnlyPaths;
        }

        public void setOperatorOnlyPaths(List<String> operatorOnlyPaths) {
            this.operatorOnlyPaths = operatorOnlyPaths;
        }

        public Twitch getTwitch() {
            return twitch;
        }

        /** Whether a request path falls under the auth gate: protected and not explicitly excluded. */
        public boolean protects(String path) {
            return excludedPaths.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, path))
                    && protectedPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
        }

        /** Whether a Twitch login is on the operator list (case-insensitive, Twitch logins are lower case). */
        public boolean isOperator(String login) {
            return login != null
                    && operators.stream().anyMatch(operator -> operator.trim().equalsIgnoreCase(login));
        }

        /** Whether a request needs the operator role: a non-read on one of the operator-only paths. */
        public boolean requiresOperator(HttpMethod method, String path) {
            if (method == null
                    || HttpMethod.GET.equals(method)
                    || HttpMethod.HEAD.equals(method)
                    || HttpMethod.OPTIONS.equals(method)) {
                return false;
            }
            return operatorOnlyPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
        }
    }

    /** Sign in with Twitch: the gateway is the OAuth client and mints its own token for the console. */
    public static class Twitch {

        private boolean enabled;
        private String clientId;
        private String clientSecret;
        private String authorizeUrl = "https://id.twitch.tv/oauth2/authorize";
        private String tokenUrl = "https://id.twitch.tv/oauth2/token";
        private String validateUrl = "https://id.twitch.tv/oauth2/validate";
        private String revokeUrl = "https://id.twitch.tv/oauth2/revoke";
        // Registered on the Twitch application exactly; its origin is where the browser is sent back to.
        private String redirectUri;
        private Duration tokenTtl = Duration.ofDays(7);
        private Duration stateTtl = Duration.ofMinutes(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAuthorizeUrl() {
            return authorizeUrl;
        }

        public void setAuthorizeUrl(String authorizeUrl) {
            this.authorizeUrl = authorizeUrl;
        }

        public String getTokenUrl() {
            return tokenUrl;
        }

        public void setTokenUrl(String tokenUrl) {
            this.tokenUrl = tokenUrl;
        }

        public String getValidateUrl() {
            return validateUrl;
        }

        public void setValidateUrl(String validateUrl) {
            this.validateUrl = validateUrl;
        }

        public String getRevokeUrl() {
            return revokeUrl;
        }

        public void setRevokeUrl(String revokeUrl) {
            this.revokeUrl = revokeUrl;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public Duration getTokenTtl() {
            return tokenTtl;
        }

        public void setTokenTtl(Duration tokenTtl) {
            this.tokenTtl = tokenTtl;
        }

        public Duration getStateTtl() {
            return stateTtl;
        }

        public void setStateTtl(Duration stateTtl) {
            this.stateTtl = stateTtl;
        }
    }

    public static class RateLimitRule {

        private String id;
        private String path;
        private String method = "POST";
        private int requests = 30;
        private int windowSeconds = 60;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public int getRequests() {
            return requests;
        }

        public void setRequests(int requests) {
            this.requests = requests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
