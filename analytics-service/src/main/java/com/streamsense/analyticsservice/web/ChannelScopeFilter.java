package com.streamsense.analyticsservice.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.service.DealService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Confines a streamer to their own channel. The gateway says who a request is for in two headers it
 * sets itself ({@value #LOGIN_HEADER}, {@value #ROLE_HEADER}); with any role but the operator's (the
 * gateway issues only {@code operator} and {@code streamer}), a request may name only that login: in
 * the {@code /streams/{streamer}} path, the {@code streamer} query parameter, the {@code streamer} of
 * a deal being created, or the owner of a deal or session addressed by id. The operator's routes
 * ({@link #OPERATOR_ONLY}: the Helix poller's status, whose last error is raw, and the sponsors every
 * channel's deals name) and a deal's deletion are refused to that role outright. Requests without the
 * headers (the gateway's own resolvers, the smoke tests) are unscoped, which is safe only because this
 * service is reachable through the gateway alone.
 */
@Component
public class ChannelScopeFilter extends OncePerRequestFilter {

    public static final String LOGIN_HEADER = "X-StreamSense-Auth-Login";
    public static final String ROLE_HEADER = "X-StreamSense-Auth-Role";
    static final String ROLE_OPERATOR = "operator";

    private static final Pattern STREAMS = Pattern.compile("^/api/analytics/streams/([^/]+)(?:/.*)?$");
    private static final Pattern DEAL = Pattern.compile("^/api/analytics/deals/(\\d+)(?:/.*)?$");
    /** The deal itself, not its share link: deleting one is the operator's call. */
    private static final Pattern DEAL_ITSELF = Pattern.compile("^/api/analytics/deals/\\d+$");

    private static final Pattern SESSION = Pattern.compile("^/api/analytics/sessions/(\\d+)(?:/.*)?$");
    /** The Helix poller's status, whose last error is raw, and every sponsor deals name across all channels. */
    static final Set<String> OPERATOR_ONLY = Set.of("/api/analytics/helix/status", "/api/analytics/deals/sponsors");

    private static final String PROBLEM_TYPE = "https://streamsense.dev/problems/forbidden";

    private final DealService deals;
    private final StreamSessionService sessions;
    private final ObjectMapper objectMapper;
    private final String serviceName;

    public ChannelScopeFilter(
            DealService deals,
            StreamSessionService sessions,
            ObjectMapper objectMapper,
            @Value("${spring.application.name:analytics-service}") String serviceName) {
        this.deals = deals;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String role = request.getHeader(ROLE_HEADER);
        String login = request.getHeader(LOGIN_HEADER);
        // Only the operator role is unconfined. A login with any other role, or none (the gateway refuses
        // such tokens, but this service does not rely on that), is confined like a streamer.
        if (login == null || login.isBlank() || ROLE_OPERATOR.equals(role)) {
            chain.doFilter(request, response);
            return;
        }
        HttpServletRequest scoped = request;
        // Spring matches routes with matrix parameters (";key=value") removed from each segment; compare the
        // same way, or "/helix/status;x" would reach the controller unscoped.
        String path = withoutMatrixParameters(request.getRequestURI());
        if (OPERATOR_ONLY.contains(path)
                || (DEAL_ITSELF.matcher(path).matches() && "DELETE".equalsIgnoreCase(request.getMethod()))) {
            forbid(request, response, "operator_required", "This is for operators");
            return;
        }
        String owner = null;
        Matcher streams = STREAMS.matcher(path);
        Matcher deal = DEAL.matcher(path);
        Matcher session = SESSION.matcher(path);
        if (streams.matches()) {
            owner = streams.group(1);
        } else if (path.equals("/api/analytics/deals") && "POST".equalsIgnoreCase(request.getMethod())) {
            // The body names the streamer; read it once here and hand the controller a replayable copy.
            byte[] body = request.getInputStream().readAllBytes();
            scoped = new ReplayableRequest(request, body);
            owner = streamerIn(body);
            if (owner == null) {
                owner = "";
            }
        } else if (path.equals("/api/analytics/deals")) {
            owner = Optional.ofNullable(request.getParameter("streamer")).orElse("");
        } else if (deal.matches()) {
            owner = deals.get(Long.parseLong(deal.group(1)))
                    .map(found -> found.streamer())
                    .orElse(null);
        } else if (session.matches()) {
            owner = sessions.get(Long.parseLong(session.group(1)))
                    .map(found -> found.streamer())
                    .orElse(null);
        }
        if (owner != null && !normalize(owner).equals(normalize(login))) {
            forbid(request, response, "channel_forbidden", "This channel is not yours to see");
            return;
        }
        chain.doFilter(scoped, response);
    }

    private String streamerIn(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode streamer = node.path("streamer");
            return streamer.isTextual() ? streamer.asText() : null;
        } catch (IOException exception) {
            // Not JSON, or not an object: the controller answers with its own 400.
            return null;
        }
    }

    private void forbid(HttpServletRequest request, HttpServletResponse response, String reason, String detail)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", PROBLEM_TYPE);
        body.put("title", HttpStatus.FORBIDDEN.getReasonPhrase());
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("service", serviceName);
        body.put("timestamp", Instant.now().toString());
        body.put("reason", reason);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(objectMapper.writeValueAsBytes(body));
    }

    static String withoutMatrixParameters(String uri) {
        return uri.replaceAll(";[^/]*", "");
    }

    static String normalize(String channel) {
        return channel == null ? "" : channel.trim().replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
    }

    /** The request with its body read once and served again to the controller. */
    private static final class ReplayableRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        ReplayableRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            // A byte-array stream: its reads cannot fail, so the servlet stream's checked signatures do not apply.
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("blocking read only");
                }

                @Override
                public int read() {
                    return source.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
