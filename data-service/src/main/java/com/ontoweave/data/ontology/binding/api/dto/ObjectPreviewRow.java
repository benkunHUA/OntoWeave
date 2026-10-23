package com.ontoweave.data.ontology.binding.api.dto;

import java.util.Map;

public record ObjectPreviewRow(
        String primaryKey,
        String title,
        Map<String, Object> properties
) {

    public ObjectPreviewRow {
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }
}
