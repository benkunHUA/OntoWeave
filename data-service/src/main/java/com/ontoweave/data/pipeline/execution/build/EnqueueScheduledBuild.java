package com.ontoweave.data.pipeline.execution.build;

import java.util.List;
import java.util.UUID;

public record EnqueueScheduledBuild(
        UUID pipelineId,
        UUID pipelineVersionId,
        UUID requestedBy,
        UUID operationId,
        List<ResolvedInput> resolvedInputs,
        String logicHash
) {

    public EnqueueScheduledBuild {
        resolvedInputs = resolvedInputs == null ? List.of() : List.copyOf(resolvedInputs);
    }
}
