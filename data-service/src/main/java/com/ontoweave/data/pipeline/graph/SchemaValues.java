package com.ontoweave.data.pipeline.graph;

import com.fasterxml.jackson.databind.JsonNode;

final class SchemaValues {

    private SchemaValues() {
    }

    static String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
