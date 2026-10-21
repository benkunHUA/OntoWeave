package com.ontoweave.data.pipeline.api.dto;

import java.util.List;
import java.util.UUID;

public record SourceResponse(
        UUID pipelineId,
        UUID datasetId,
        UUID versionId,
        SourceOutput output,
        SourceBuild build,
        SourcePipelineVersion pipelineVersion,
        List<SourceInput> inputs
) {

    public SourceResponse {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
    }
}
