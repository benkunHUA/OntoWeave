package com.ontoweave.data.sync.api.dto;

import java.time.Instant;
import java.util.UUID;

public record DatasetListItemResponse(
        UUID id,
        UUID connectionId,
        UUID syncTaskId,
        String name,
        UUID currentVersionId,
        DatasetVersionResponse currentVersion,
        int versionCount,
        String sourceSchema,
        String sourceTable,
        Instant createdAt,
        Instant updatedAt
) {
}
