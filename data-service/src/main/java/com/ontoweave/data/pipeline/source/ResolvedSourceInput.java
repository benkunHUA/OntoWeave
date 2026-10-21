package com.ontoweave.data.pipeline.source;

import java.util.UUID;

record ResolvedSourceInput(
        DatasetRef ref,
        UUID datasetVersionId,
        Integer datasetVersionNumber,
        String schemaHash,
        DisplaySnapshot displaySnapshot
) {
}
