package com.ontoweave.data.ontology.index.pipeline;

import java.util.List;
import java.util.UUID;

public record RelationBuildBatch(List<RelationBuildResult> relations, List<UUID> skippedRelationIds) {

    public RelationBuildBatch {
        relations = relations == null ? List.of() : List.copyOf(relations);
        skippedRelationIds = skippedRelationIds == null ? List.of() : List.copyOf(skippedRelationIds);
    }
}
