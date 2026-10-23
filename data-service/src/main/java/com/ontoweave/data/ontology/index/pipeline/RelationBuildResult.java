package com.ontoweave.data.ontology.index.pipeline;

import java.util.UUID;

public record RelationBuildResult(
        UUID relationVersionId,
        UUID relationId,
        long rowCount,
        long skippedCount
) {
}
