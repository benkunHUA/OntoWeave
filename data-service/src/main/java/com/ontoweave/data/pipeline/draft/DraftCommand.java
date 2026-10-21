package com.ontoweave.data.pipeline.draft;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddNode.class, name = "add_node"),
        @JsonSubTypes.Type(value = UpdateNode.class, name = "update_node"),
        @JsonSubTypes.Type(value = DeleteNode.class, name = "delete_node"),
        @JsonSubTypes.Type(value = ConnectNodes.class, name = "connect"),
        @JsonSubTypes.Type(value = DisconnectNodes.class, name = "disconnect"),
        @JsonSubTypes.Type(value = SetOutput.class, name = "set_output")
})
public sealed interface DraftCommand permits AddNode, UpdateNode, DeleteNode, ConnectNodes,
        DisconnectNodes, SetOutput {

    Long baseRevision();

    UUID operationId();

    UUID turnId();
}
