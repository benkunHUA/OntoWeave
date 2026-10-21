package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @JsonCreator
    static ConnectNodes fromJson(
            @JsonProperty("baseRevision") long baseRevision,
            @JsonProperty("operationId") UUID operationId,
            @JsonProperty("turnId") UUID turnId,
            @JsonProperty("edge") DraftEdgePayload edge,
            @JsonProperty("edgeId") UUID edgeId,
            @JsonProperty("sourceNodeId") UUID sourceNodeId,
            @JsonProperty("sourcePort") String sourcePort,
            @JsonProperty("targetNodeId") UUID targetNodeId,
            @JsonProperty("targetPort") String targetPort
    ) {
        if (edge != null) {
            return new ConnectNodes(
                    baseRevision,
                    operationId,
                    turnId,
                    edge.id(),
                    edge.sourceNodeId(),
                    edge.sourcePort(),
                    edge.targetNodeId(),
                    edge.targetPort()
            );
        }
        return new ConnectNodes(
                baseRevision,
                operationId,
                turnId,
                edgeId,
                sourceNodeId,
                sourcePort,
                targetNodeId,
                targetPort
        );
    }
}
