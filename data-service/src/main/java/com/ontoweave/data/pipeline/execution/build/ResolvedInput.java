package com.ontoweave.data.pipeline.execution.build;

import com.ontoweave.data.pipeline.source.DisplaySnapshot;

import java.util.UUID;

public record ResolvedInput(
        String datasetKind,
        UUID datasetId,
        UUID datasetVersionId,
        int datasetVersionNumber,
        String schemaHash,
        String icebergCatalog,
        String icebergNamespace,
        String icebergTable,
        long icebergSnapshotId,
        DisplaySnapshot displaySnapshot
) {
}
