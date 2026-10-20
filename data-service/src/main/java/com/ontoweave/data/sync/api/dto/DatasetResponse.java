package com.ontoweave.data.sync.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DatasetResponse(
        UUID id,
        UUID connectionId,
        UUID syncTaskId,
        String name,
        String activeIcebergCatalog,
        String activeIcebergNamespace,
        String activeIcebergTable,
        UUID currentVersionId,
        DatasetVersionResponse currentVersion,
        List<DatasetVersionResponse> versions,
        int versionCount,
        String sourceSchema,
        String sourceTable,
        Instant createdAt,
        Instant updatedAt
) {
}
