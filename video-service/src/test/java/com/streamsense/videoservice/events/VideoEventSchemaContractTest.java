package com.streamsense.videoservice.events;

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

class VideoEventSchemaContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void documentedFrameSchemaMatchesFrameDataShape() throws IOException {
        JsonNode schema = readSchema("frame-data.schema.json");
        FrameData frame = new FrameData();
        frame.setFrameId("frame-1");
        frame.setStreamer("streamer-1");
        frame.setFrameRef("frames/frame-1.png");
        frame.setFrameSequence(1L);
        frame.setCapturedAt(1710000000000L);

        assertThat(propertyNames(schema)).isEqualTo(serializedNames(frame));
        assertThat(requiredNames(schema)).isEqualTo(serializedNames(frame));
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void documentedSponsorSchemaMatchesSponsorDetectionShape() throws IOException {
        JsonNode schema = readSchema("sponsor-detection-event.schema.json");
        SponsorDetectionEvent event = new SponsorDetectionEvent();
        event.setDetectionEventId("det-1");
        event.setSourceFrameId("frame-1");
        event.setStreamer("streamer-1");
        event.setFrameRef("frames/frame-1.png");
        event.setFrameSequence(1L);
        event.setCapturedAt(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setSponsor("Nike");
        event.setConfidence(0.91d);
        event.setModelVersion("stub-v1");
        event.setX(0.1d);
        event.setY(0.2d);
        event.setWidth(0.3d);
        event.setHeight(0.4d);

        assertThat(propertyNames(schema)).isEqualTo(serializedNames(event));
        assertThat(requiredNames(schema)).isEqualTo(serializedNames(event));
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
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

    private Set<String> serializedNames(Object value) {
        Map<String, Object> values = objectMapper.convertValue(value, new TypeReference<>() {
        });
        return new TreeSet<>(values.keySet());
    }
}
