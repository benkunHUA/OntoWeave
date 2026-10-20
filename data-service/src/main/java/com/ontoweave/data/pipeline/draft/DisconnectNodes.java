package com.ontoweave.data.pipeline.draft;

import java.util.UUID;

public record DisconnectNodes(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID edgeId
) implements DraftCommand {

    public DisconnectNodes(UUID edgeId, long baseRevision, UUID operationId, UUID turnId) {
        this(baseRevision, operationId, turnId, edgeId);
    }
}
