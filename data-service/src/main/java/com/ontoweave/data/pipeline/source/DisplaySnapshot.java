package com.ontoweave.data.pipeline.source;

import java.util.UUID;

record DisplaySnapshot(
        String datasetName,
        Integer datasetVersionNumber,
        UUID connectionId,
        String connectionName,
        String sourceSchemaName,
        String sourceTableName
) {
}
