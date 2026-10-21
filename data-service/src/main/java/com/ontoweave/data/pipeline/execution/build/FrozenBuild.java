package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.domain.PipelineVersionEntity;

import java.util.List;

public record FrozenBuild(
        PipelineVersionEntity version,
        List<ResolvedInput> resolvedInputs
) {

    public FrozenBuild {
        resolvedInputs = resolvedInputs == null ? List.of() : List.copyOf(resolvedInputs);
    }
}
