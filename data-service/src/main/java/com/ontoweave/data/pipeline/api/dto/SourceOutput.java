package com.ontoweave.data.pipeline.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SourceOutput(
        UUID outputId,
        String name,
        int versionNumber,
        long rowCount,
        Instant publishedAt
) {
}
