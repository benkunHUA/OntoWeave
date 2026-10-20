package com.ontoweave.data.pipeline.graph;

import java.util.UUID;

public record DraftEdge(
        UUID id,
        UUID sourceNodeId,
        String sourcePort,
        UUID targetNodeId,
        String targetPort
) {
}
