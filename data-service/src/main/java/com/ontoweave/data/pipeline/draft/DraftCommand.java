package com.ontoweave.data.pipeline.draft;

import java.util.UUID;

public sealed interface DraftCommand permits AddNode, UpdateNode, DeleteNode, ConnectNodes,
        DisconnectNodes, SetOutput {

    long baseRevision();

    UUID operationId();

    UUID turnId();
}
