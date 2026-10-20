package com.ontoweave.data.pipeline.catalog;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public record NodeDefinition(
        String type,
        int version,
        String label,
        String category,
        List<NodePort> ports,
        ObjectNode configSchema,
        ObjectNode uiSchema
) {

    public NodeDefinition {
        ports = List.copyOf(ports);
        configSchema = configSchema.deepCopy();
        uiSchema = uiSchema.deepCopy();
    }

    @Override
    public ObjectNode configSchema() {
        return configSchema.deepCopy();
    }

    @Override
    public ObjectNode uiSchema() {
        return uiSchema.deepCopy();
    }
}
