package com.ontoweave.data.ontology.index.pipeline;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ObjectBuildResult(
        UUID objectVersionId,
        long rowCount,
        int warningCount,
        List<RelationBuildResult> relations,
        Map<String, Object> stats
) {

    public ObjectBuildResult {
        relations = relations == null ? List.of() : List.copyOf(relations);
    }
}
