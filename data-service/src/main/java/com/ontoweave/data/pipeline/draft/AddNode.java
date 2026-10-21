package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record AddNode(
        long baseRevision,
        UUID operationId,
        UUID turnId,
        UUID nodeId,
        String type,
        String name,
        JsonNode config,
        JsonNode position
) implements DraftCommand {

    public AddNode(
            UUID nodeId,
            String type,
            String name,
            JsonNode config,
            JsonNode position,
            long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, nodeId, type, name, config, position);
    }

    @JsonCreator
    static AddNode fromJson(
            @JsonProperty("baseRevision") long baseRevision,
            @JsonProperty("operationId") UUID operationId,
            @JsonProperty("turnId") UUID turnId,
            @JsonProperty("node") DraftNodePayload node,
            @JsonProperty("nodeId") UUID nodeId,
            @JsonProperty("nodeType") String nodeType,
            @JsonProperty("name") String name,
            @JsonProperty("config") JsonNode config,
            @JsonProperty("position") JsonNode position
    ) {
        if (node != null) {
            return new AddNode(
                    baseRevision,
                    operationId,
                    turnId,
                    node.id(),
                    node.type(),
                    node.name() == null ? name : node.name(),
                    node.config() == null ? config : node.config(),
                    node.position() == null ? position : node.position()
            );
        }
        return new AddNode(baseRevision, operationId, turnId, nodeId, nodeType, name, config, position);
    }
}
