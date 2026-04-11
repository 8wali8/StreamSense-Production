package com.streamsense.apigateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "streamsense.gateway")
public class GatewayEdgeProperties {

    private final Auth auth = new Auth();

    public Auth getAuth() {
        return auth;
    }

    public static class Auth {

        private boolean enabled;
        private List<String> protectedPaths = List.of("/graphql", "/api/**");
        private List<String> excludedPaths = List.of("/actuator/**", "/graphiql", "/graphiql/**");
        private String requiredIssuer = "streamsense-local";
        private String requiredAudience = "streamsense-clients";

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
    }

}
