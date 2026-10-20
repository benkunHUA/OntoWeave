package com.ontoweave.data.pipeline.draft;

import java.util.UUID;

public record DeleteNode(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID nodeId
) implements DraftCommand {

    public DeleteNode(UUID nodeId, long baseRevision, UUID operationId, UUID turnId) {
        this(baseRevision, operationId, turnId, nodeId);
    }
}
