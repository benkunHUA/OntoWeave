package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record UpdateNode(
        Long baseRevision,
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
            Long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, nodeId, name, config);
    }

    @JsonCreator
    static UpdateNode fromJson(
            @JsonProperty("baseRevision") Long baseRevision,
            @JsonProperty("operationId") UUID operationId,
            @JsonProperty("turnId") UUID turnId,
            @JsonProperty("node") DraftNodePayload node,
            @JsonProperty("nodeId") UUID nodeId,
            @JsonProperty("name") String name,
            @JsonProperty("config") JsonNode config
    ) {
        if (node != null) {
            return new UpdateNode(
                    baseRevision,
                    operationId,
                    turnId,
                    node.id(),
                    node.name() == null ? name : node.name(),
                    node.config() == null ? config : node.config()
            );
        }
        return new UpdateNode(baseRevision, operationId, turnId, nodeId, name, config);
    }
}
