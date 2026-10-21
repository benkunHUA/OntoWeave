package com.ontoweave.data.pipeline.execution.output;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record OutputSnapshot(
        UUID datasetId,
        String icebergCatalog,
        String icebergNamespace,
        String icebergTable,
        String branch,
        long snapshotId,
        JsonNode schema,
        long rowCount
) {

    public static OutputSnapshot from(WrittenSnapshot written) {
        return new OutputSnapshot(
                written.datasetId(),
                written.icebergCatalog(),
                written.icebergNamespace(),
                written.icebergTable(),
                written.branch(),
                written.icebergSnapshotId(),
                written.schema(),
                written.rowCount()
        );
    }
}
