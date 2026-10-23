package com.ontoweave.data.ontology.dataset;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DatasetSchema {

    private DatasetSchema() {
    }

    public static List<DatasetColumn> columns(JsonNode schemaJson) {
        if (schemaJson == null || !schemaJson.isObject()) {
            return List.of();
        }
        JsonNode fields = schemaJson.path("fields");
        if (!fields.isArray()) {
            return List.of();
        }
        List<DatasetColumn> columns = new ArrayList<>();
        for (JsonNode field : fields) {
            String name = field.path("name").asText(null);
            if (name == null || name.isBlank()) {
                continue;
            }
            String type = field.path("type").asText("unknown");
            boolean optional = !field.path("required").asBoolean(false);
            Object example = field.has("example") && !field.path("example").isNull()
                    ? field.path("example").asText()
                    : null;
            columns.add(new DatasetColumn(name, type, optional, example));
        }
        return List.copyOf(columns);
    }

    public static Map<String, DatasetColumn> columnsByName(JsonNode schemaJson) {
        Map<String, DatasetColumn> columnsByName = new LinkedHashMap<>();
        for (DatasetColumn column : columns(schemaJson)) {
            columnsByName.put(column.name(), column);
        }
        return Map.copyOf(columnsByName);
    }
}
