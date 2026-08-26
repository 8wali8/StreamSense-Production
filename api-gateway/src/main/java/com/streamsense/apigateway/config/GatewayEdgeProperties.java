package com.streamsense.apigateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;

@ConfigurationProperties(prefix = "streamsense.gateway")
public class GatewayEdgeProperties {

    private final Auth auth = new Auth();
    private final List<RateLimitRule> rateLimits = new ArrayList<>();
    private boolean rateLimitEnabled = true;
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

        /** Whether a request path falls under the auth gate: protected and not explicitly excluded. */
        public boolean protects(String path) {
            return excludedPaths.stream().noneMatch(pattern -> PATH_MATCHER.match(pattern, path))
                    && protectedPaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
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
