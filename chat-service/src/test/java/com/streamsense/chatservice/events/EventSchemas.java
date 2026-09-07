package com.streamsense.chatservice.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Validates serialised events against the JSON Schemas under {@code docs/schemas}, which are the published
 * contract between services. Surefire runs with the module directory as the working directory, so the
 * schemas are one level up.
 */
final class EventSchemas {

    private static final Path SCHEMA_DIR = Path.of("..", "docs", "schemas");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    private EventSchemas() {}

    static JsonNode toJson(Object event) {
        return MAPPER.valueToTree(event);
    }

    static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Validation messages for {@code document} against {@code schemaFile}; empty when the document conforms. */
    static Set<String> violations(String schemaFile, JsonNode document) {
        try {
            JsonSchema schema = FACTORY.getSchema(Files.readString(SCHEMA_DIR.resolve(schemaFile)));
            return schema.validate(document).stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
