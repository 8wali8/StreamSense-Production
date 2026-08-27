package com.streamsense.chatservice.events;

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

class ChatMessageSchemaContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void documentedSchemaMatchesChatMessageEventShape() throws IOException {
        JsonNode schema = readSchema("chat-message-event.json");
        ChatMessageEvent event = new ChatMessageEvent("evt-1", "streamer-1", "user-1", "hello", 1710000000000L);

        assertThat(propertyNames(schema)).isEqualTo(serializedNames(event));
        assertThat(requiredNames(schema)).isEqualTo(serializedNames(event));
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("properties").path("timestamp").path("type").asText()).isEqualTo("integer");
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

    private Set<String> serializedNames(ChatMessageEvent event) {
        Map<String, Object> values = objectMapper.convertValue(event, new TypeReference<>() {
        });
        return new TreeSet<>(values.keySet());
    }
}
