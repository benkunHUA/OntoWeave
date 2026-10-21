package com.ontoweave.data.pipeline.execution.build;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record BuildOutputResponse(
        UUID id,
        UUID outputId,
        UUID datasetId,
        UUID datasetVersionId,
        String outputType,
        String status,
        String stage,
        String engineJobId,
        String logUri,
        String errorCode,
        String errorMessage,
        JsonNode result,
        JsonNode stats,
        Instant startedAt,
        Instant finishedAt
) {
}
