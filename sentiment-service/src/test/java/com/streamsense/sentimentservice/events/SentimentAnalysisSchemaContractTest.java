package com.streamsense.sentimentservice.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SentimentAnalysisSchemaContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void documentedSchemaMatchesSentimentEventShape() throws IOException {
        JsonNode schema = readSchema("sentiment-analysis-event.json");
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId("sent-1");
        event.setSourceEventId("evt-1");
        event.setStreamer("streamer-1");
        event.setUser("user-1");
        event.setMessage("great stream");
        event.setChatTimestamp(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setLabel("POSITIVE");
        event.setScore(0.87d);
        event.setModelVersion("stub-v1");

        assertThat(propertyNames(schema)).isEqualTo(serializedNames(event));
        assertThat(requiredNames(schema)).isEqualTo(serializedNames(event));
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(enumValues(schema, "label")).containsExactly("NEGATIVE", "NEUTRAL", "POSITIVE");
    }

    private JsonNode readSchema(String fileName) throws IOException {
        return objectMapper.readTree(Files.readString(Path.of("..", "docs", "schemas", fileName)));
    }

    private Set<String> propertyNames(JsonNode schema) {
        Set<String> names = new TreeSet<>();
        Iterator<String> fieldNames = schema.path("properties").fieldNames();
        while (fieldNames.hasNext()) {
            names.add(fieldNames.next());
        }
        return names;
    }

    private Set<String> requiredNames(JsonNode schema) {
        Set<String> names = new TreeSet<>();
        for (JsonNode node : schema.path("required")) {
            names.add(node.asText());
        }
        return names;
    }

    private Set<String> serializedNames(SentimentAnalysisEvent event) {
        Map<String, Object> values = objectMapper.convertValue(event, new TypeReference<>() {
        });
        return new TreeSet<>(values.keySet());
    }

    private Set<String> enumValues(JsonNode schema, String property) {
        Set<String> names = new TreeSet<>();
        for (JsonNode node : schema.path("properties").path(property).path("enum")) {
            names.add(node.asText());
        }
        return names;
    }
}
