package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonCreator
    static DeleteNode fromJson(
            @JsonProperty("baseRevision") long baseRevision,
            @JsonProperty("operationId") UUID operationId,
            @JsonProperty("turnId") UUID turnId,
            @JsonProperty("node") DraftNodePayload node,
            @JsonProperty("nodeId") UUID nodeId
    ) {
        return new DeleteNode(
                baseRevision,
                operationId,
                turnId,
                node == null ? nodeId : node.id()
        );
    }
}
