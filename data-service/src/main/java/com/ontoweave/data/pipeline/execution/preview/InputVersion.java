package com.ontoweave.data.pipeline.execution.preview;

import com.ontoweave.data.pipeline.execution.build.DisplaySnapshot;
import com.ontoweave.data.pipeline.execution.build.ResolvedInput;

import java.util.UUID;

public record InputVersion(
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

    public static InputVersion from(ResolvedInput input) {
        return new InputVersion(
                input.datasetKind(),
                input.datasetId(),
                input.datasetVersionId(),
                input.datasetVersionNumber(),
                input.schemaHash(),
                input.icebergCatalog(),
                input.icebergNamespace(),
                input.icebergTable(),
                input.icebergSnapshotId(),
                input.displaySnapshot()
        );
    }
}
