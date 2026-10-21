package com.ontoweave.data.pipeline.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SourceBuild(
        UUID buildId,
        String status,
        String trigger,
        Instant startedAt,
        Instant finishedAt,
        String logUri
) {
}
