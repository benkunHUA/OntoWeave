package com.ontoweave.data.pipeline.graph;

import java.util.Optional;
import java.util.UUID;

public interface InputSchemaResolver {

    Optional<InputSchema> resolve(UUID ownerId, String datasetKind, UUID datasetId);
}
