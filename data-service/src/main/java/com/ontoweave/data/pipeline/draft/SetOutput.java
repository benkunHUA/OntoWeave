package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record SetOutput(
        Long baseRevision,
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
            Long baseRevision,
            UUID operationId,
            UUID turnId
    ) {
        this(baseRevision, operationId, turnId, nodeId, name, writeMode, primaryKeyColumns, expectations);
    }

    @JsonCreator
    static SetOutput fromJson(
            @JsonProperty("baseRevision") Long baseRevision,
            @JsonProperty("operationId") UUID operationId,
            @JsonProperty("turnId") UUID turnId,
            @JsonProperty("output") DraftOutputPayload output,
            @JsonProperty("nodeId") UUID nodeId,
            @JsonProperty("name") String name,
            @JsonProperty("writeMode") String writeMode,
            @JsonProperty("primaryKeyColumns") JsonNode primaryKeyColumns,
            @JsonProperty("expectations") JsonNode expectations
    ) {
        if (output != null) {
            return new SetOutput(
                    baseRevision,
                    operationId,
                    turnId,
                    output.nodeId(),
                    output.name(),
                    output.writeMode(),
                    output.primaryKeyColumns(),
                    output.expectations()
            );
        }
        return new SetOutput(
                baseRevision,
                operationId,
                turnId,
                nodeId,
                name,
                writeMode,
                primaryKeyColumns,
                expectations
        );
    }
}
