package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record AddNode(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID nodeId,
        String type,
        String name,
        JsonNode config,
        JsonNode position
) implements DraftCommand {

    public AddNode(
            UUID nodeId,
            String type,
            String name,
            JsonNode config,
            JsonNode position,
            long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, nodeId, type, name, config, position);
    }
}
