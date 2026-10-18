package com.ontoweave.data.sync.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SyncTaskResponse(
        UUID id,
        UUID connectionId,
        UUID datasetId,
        String schema,
        String table,
        String syncMode,
        int versionIntervalSeconds,
        List<String> primaryKeyColumns,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
