package com.ontoweave.data.pipeline.graph;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface InputSchemaResolver {

    String SYNC_DATASET_KIND = "sync";

    Optional<InputSchema> resolve(UUID ownerId, String datasetKind, UUID datasetId);

    default Map<UUID, InputSchema> resolveAll(UUID ownerId, Set<UUID> datasetIds) {
        Map<UUID, InputSchema> resolved = new LinkedHashMap<>();
        for (UUID datasetId : datasetIds) {
            resolve(ownerId, SYNC_DATASET_KIND, datasetId)
                    .ifPresent(schema -> resolved.put(datasetId, schema));
        }
        return resolved;
    }
}
