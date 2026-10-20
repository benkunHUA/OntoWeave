package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record SetOutput(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID nodeId,
        String name,
        String writeMode,
        JsonNode primaryKeyColumns,
        JsonNode expectations
) implements DraftCommand {

    public SetOutput(
            UUID nodeId,
            String name,
            String writeMode,
            JsonNode primaryKeyColumns,
            JsonNode expectations,
            long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, nodeId, name, writeMode, primaryKeyColumns, expectations);
    }
}
