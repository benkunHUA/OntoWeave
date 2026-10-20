package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

public record OutputConfiguration(UUID outputId, JsonNode primaryKeyColumns, JsonNode expectations) {

    private static final ObjectMapper JSON = new ObjectMapper();

    public OutputConfiguration {
        primaryKeyColumns = primaryKeyColumns == null ? JSON.createArrayNode() : primaryKeyColumns.deepCopy();
        expectations = expectations == null ? JSON.createArrayNode() : expectations.deepCopy();
    }

    public OutputConfiguration(JsonNode config) {
        this(
                config == null ? null : uuid(config.path("outputId").asText(null)),
                config == null ? null : config.get("primaryKeyColumns"),
                config == null ? null : config.get("expectations")
        );
    }

    private static UUID uuid(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
