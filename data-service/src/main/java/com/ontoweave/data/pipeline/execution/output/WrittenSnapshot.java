package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WrittenSnapshot(
        UUID datasetId,
        String icebergCatalog,
        String icebergNamespace,
        String icebergTable,
        String branch,
        long icebergSnapshotId,
        JsonNode schema,
        long rowCount,
        long byteSize,
        String storagePath,
        Instant committedAt,
        String engineJobId,
        List<Map<String, Object>> rows
) {

    public WrittenSnapshot {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
