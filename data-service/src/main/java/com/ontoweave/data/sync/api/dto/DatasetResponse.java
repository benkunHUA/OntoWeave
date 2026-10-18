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
        Instant createdAt,
        Instant updatedAt
) {
}
