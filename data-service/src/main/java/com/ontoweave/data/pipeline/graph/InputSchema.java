package com.ontoweave.data.pipeline.graph;

import java.util.List;
import java.util.UUID;

public record InputSchema(UUID datasetVersionId, List<FieldSchema> fields) {

    public InputSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
