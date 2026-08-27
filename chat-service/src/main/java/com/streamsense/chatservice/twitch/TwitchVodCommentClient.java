package com.streamsense.chatservice.twitch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.chatservice.config.StreamSenseProperties;

@Component
public class TwitchVodCommentClient {

    private static final String COMMENTS_QUERY = """
            query VideoCommentsByOffsetOrCursor($videoID: ID!, $contentOffsetSeconds: Int!, $cursor: Cursor) {
              video(id: $videoID) {
                comments(contentOffsetSeconds: $contentOffsetSeconds, after: $cursor) {
                  edges {
                    cursor
                    node {
                      id
                      contentOffsetSeconds
                      commenter { login displayName }
                      message { fragments { text } }
                    }
                  }
                  pageInfo { hasNextPage }
                }
              }
            }
            """;

    private final StreamSenseProperties.TwitchGraphql properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, List<TwitchVodChatComment>> cache = new ConcurrentHashMap<>();

    public TwitchVodCommentClient(StreamSenseProperties streamSenseProperties, ObjectMapper objectMapper) {
        this.properties = streamSenseProperties.getReplay().getTwitchGraphql();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, properties.getRequestTimeoutMs())))
                .build();
    }

    public List<TwitchVodChatComment> fetchComments(String vodId, double startOffsetSeconds) {
        return cache.computeIfAbsent(vodId, ignored -> downloadComments(vodId, startOffsetSeconds));
    }

    public List<TwitchVodChatComment> fetchComments(StreamSenseProperties.ReplayAlias alias) {
        String fixturePath = alias.getChatFixturePath();
        if (fixturePath != null && !fixturePath.isBlank()) {
            return cache.computeIfAbsent("fixture:" + fixturePath, ignored -> loadFixture(alias.getVodId(), fixturePath));
        }
        return fetchComments(alias.getVodId(), alias.getStartOffsetSeconds());
    }

    private List<TwitchVodChatComment> loadFixture(String vodId, String fixturePath) {
        try (InputStream stream = openFixture(fixturePath)) {
            JsonNode root = objectMapper.readTree(stream);
            JsonNode commentsNode = root.isArray() ? root : root.path("comments");
            List<TwitchVodChatComment> comments = new ArrayList<>();
            if (commentsNode.isArray()) {
                int sequence = 0;
                for (JsonNode node : commentsNode) {
                    sequence++;
                    String id = node.path("id").asText("fixture-" + sequence).trim();
                    String user = node.path("user").asText("vod-user").trim();
                    String message = node.path("message").asText("").trim();
                    if (!message.isEmpty()) {
                        comments.add(new TwitchVodChatComment(
                                id,
                                user.isEmpty() ? "vod-user" : user,
                                message,
                                node.path("offsetSeconds").asDouble(0.0)));
                    }
                }
            }
            comments.sort(Comparator.comparingDouble(TwitchVodChatComment::offsetSeconds));
            return List.copyOf(comments);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load Twitch VOD chat fixture " + fixturePath, e);
        }
    }

    private static InputStream openFixture(String fixturePath) throws IOException {
        if (fixturePath.startsWith("classpath:")) {
            String resource = fixturePath.substring("classpath:".length()).replaceFirst("^/", "");
            InputStream stream = TwitchVodCommentClient.class.getClassLoader().getResourceAsStream(resource);
            if (stream == null) {
                throw new IOException("classpath resource not found: " + resource);
            }
            return stream;
        }
        return Files.newInputStream(Path.of(fixturePath));
    }

    private List<TwitchVodChatComment> downloadComments(String vodId, double startOffsetSeconds) {
        List<TwitchVodChatComment> comments = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < Math.max(1, properties.getMaxPages()); page++) {
            JsonNode root = requestPage(vodId, startOffsetSeconds, cursor);
            JsonNode commentsNode = root.path("data").path("video").path("comments");
            JsonNode edges = commentsNode.path("edges");
            if (!edges.isArray() || edges.isEmpty()) {
                break;
            }

            for (JsonNode edge : edges) {
                TwitchVodChatComment comment = parseComment(vodId, edge.path("node"));
                if (comment != null) {
                    comments.add(comment);
                }
                cursor = edge.path("cursor").asText(cursor);
            }

            if (!commentsNode.path("pageInfo").path("hasNextPage").asBoolean(false) || cursor == null) {
                break;
            }
        }

        comments.sort(Comparator.comparingDouble(TwitchVodChatComment::offsetSeconds));
        return List.copyOf(comments);
    }

    private JsonNode requestPage(String vodId, double startOffsetSeconds, String cursor) {
        try {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("videoID", vodId);
            variables.put("contentOffsetSeconds", Math.max(0, (int) Math.floor(startOffsetSeconds)));
            variables.put("cursor", cursor);

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("operationName", "VideoCommentsByOffsetOrCursor");
            requestBody.put("query", COMMENTS_QUERY);
            requestBody.put("variables", variables);

            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint()))
                    .timeout(Duration.ofMillis(Math.max(1000, properties.getRequestTimeoutMs())))
                    .header("Client-ID", properties.getClientId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Twitch comments GraphQL returned HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (root.hasNonNull("errors")) {
                throw new IllegalStateException("Twitch comments GraphQL returned errors: " + root.path("errors"));
            }
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("failed to download Twitch VOD comments", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while downloading Twitch VOD comments", e);
        }
    }

    private static TwitchVodChatComment parseComment(String vodId, JsonNode node) {
        String id = node.path("id").asText("").trim();
        if (id.isEmpty()) {
            id = vodId + "-" + node.path("contentOffsetSeconds").asText();
        }

        String user = node.path("commenter").path("displayName").asText("").trim();
        if (user.isEmpty()) {
            user = node.path("commenter").path("login").asText("vod-user").trim();
        }

        StringBuilder message = new StringBuilder();
        JsonNode fragments = node.path("message").path("fragments");
        if (fragments.isArray()) {
            for (JsonNode fragment : fragments) {
                message.append(fragment.path("text").asText(""));
            }
        }
        String text = message.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        return new TwitchVodChatComment(id, user, text, node.path("contentOffsetSeconds").asDouble(0.0));
    }
}
