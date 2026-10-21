package com.ontoweave.data.pipeline.api.dto;

import com.ontoweave.data.pipeline.source.MetadataStatus;

import java.util.UUID;

public record SourceInput(
        String datasetKind,
        UUID datasetId,
        UUID datasetVersionId,
        Integer datasetVersionNumber,
        String schemaHash,
        String datasetName,
        String connectionName,
        String sourceSchemaName,
        String sourceTableName,
        MetadataStatus metadataStatus
) {
}
