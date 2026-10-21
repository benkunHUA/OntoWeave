package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record DisconnectNodes(
        Long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID edgeId
) implements DraftCommand {

    public DisconnectNodes(UUID edgeId, Long baseRevision, UUID operationId, UUID turnId) {
        this(baseRevision, operationId, turnId, edgeId);
    }

    @JsonCreator
    static DisconnectNodes fromJson(
            @JsonProperty("baseRevision") Long baseRevision,
            @JsonProperty("operationId") UUID operationId,
            @JsonProperty("turnId") UUID turnId,
            @JsonProperty("edge") DraftEdgePayload edge,
            @JsonProperty("edgeId") UUID edgeId
    ) {
        return new DisconnectNodes(
                baseRevision,
                operationId,
                turnId,
                edge == null ? edgeId : edge.id()
        );
    }
}
