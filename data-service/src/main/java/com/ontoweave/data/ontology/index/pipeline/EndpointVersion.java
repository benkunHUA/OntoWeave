package com.ontoweave.data.ontology.index.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;

import java.util.UUID;

public record EndpointVersion(
        UUID objectTypeId,
        UUID versionId,
        MappingSnapshot mapping,
        JsonNode mappingSnapshot,
        long mappingRevision,
        DatasetKind sourceDatasetKind,
        UUID sourceDatasetId,
        UUID sourceDatasetVersionId,
        long sourceSnapshotId
) {
}
