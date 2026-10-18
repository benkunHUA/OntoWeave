package com.ontoweave.data.sync.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record DatasetVersionResponse(
        UUID id,
        int versionNumber,
        String icebergCatalog,
        String icebergNamespace,
        String icebergTable,
        long icebergSnapshotId,
        long rowCount,
        boolean rowCountEstimated,
        long byteSize,
        JsonNode schema,
        int columnCount,
        Instant committedAt,
        Instant publishedAt,
        String versionSource
) {
}
