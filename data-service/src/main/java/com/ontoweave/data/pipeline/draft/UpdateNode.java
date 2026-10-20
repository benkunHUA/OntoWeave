package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record UpdateNode(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID nodeId,
        String name,
        JsonNode config
) implements DraftCommand {

    public UpdateNode(
            UUID nodeId,
            String name,
            JsonNode config,
            long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, nodeId, name, config);
    }
}
