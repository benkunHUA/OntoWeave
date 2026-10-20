package com.ontoweave.data.pipeline.draft;

import java.util.UUID;

public record ConnectNodes(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID edgeId,
        UUID sourceNodeId,
        String sourcePort,
        UUID targetNodeId,
        String targetPort
) implements DraftCommand {

    public ConnectNodes(
            UUID edgeId,
            UUID sourceNodeId,
            String sourcePort,
            UUID targetNodeId,
            String targetPort,
            long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, edgeId, sourceNodeId, sourcePort, targetNodeId, targetPort);
    }
}
