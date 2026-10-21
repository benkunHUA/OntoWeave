package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OutputSnapshot(
        UUID datasetId,
        String branch,
        long snapshotId,
        JsonNode schema,
        long rowCount,
        List<Map<String, Object>> rows
) {

    public OutputSnapshot {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static OutputSnapshot from(WrittenSnapshot written) {
        return new OutputSnapshot(
                written.datasetId(),
                written.branch(),
                written.icebergSnapshotId(),
                written.schema(),
                written.rowCount(),
                written.rows()
        );
    }
}
