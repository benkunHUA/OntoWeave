package com.ontoweave.data.pipeline.source;

import java.util.UUID;

public record DatasetMetadata(
        String datasetName,
        Integer datasetVersionNumber,
        UUID connectionId,
        String connectionName,
        String sourceSchemaName,
        String sourceTableName,
        MetadataStatus status
) {

    public static DatasetMetadata missing() {
        return new DatasetMetadata(null, null, null, null, null, null, MetadataStatus.MISSING);
    }

    public static DatasetMetadata restricted() {
        return new DatasetMetadata(null, null, null, null, null, null, MetadataStatus.RESTRICTED);
    }
}
