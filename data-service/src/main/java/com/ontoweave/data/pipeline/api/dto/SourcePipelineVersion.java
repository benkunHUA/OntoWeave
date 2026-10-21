package com.ontoweave.data.pipeline.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SourcePipelineVersion(
        UUID versionId,
        int versionNumber,
        Instant createdAt
) {
}
