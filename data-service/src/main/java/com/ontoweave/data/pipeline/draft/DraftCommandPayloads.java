package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

record DraftNodePayload(
        UUID id,
        String type,
        String name,
        JsonNode config,
        JsonNode position
) {
}

record DraftEdgePayload(
        UUID id,
        UUID sourceNodeId,
        String sourcePort,
        UUID targetNodeId,
        String targetPort
) {
}

record DraftOutputPayload(
        UUID nodeId,
        String name,
        String writeMode,
        JsonNode primaryKeyColumns,
        JsonNode expectations
) {
}
