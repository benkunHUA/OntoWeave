package com.ontoweave.data.sync.iceberg;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record IcebergSnapshotInfo(
        long snapshotId,
        Instant committedAt,
        long rowCount,
        long byteSize,
        JsonNode schema,
        int columnCount,
        String location
) {
}
