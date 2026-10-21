package com.ontoweave.data.pipeline.execution.build;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BuildResponse(
        UUID id,
        UUID pipelineId,
        UUID pipelineVersionId,
        String status,
        String stage,
        String trigger,
        String errorCode,
        String errorMessage,
        String logUri,
        Instant startedAt,
        Instant finishedAt,
        List<BuildOutputResponse> outputs
) {

    public BuildResponse {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
