package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;

import java.util.UUID;

public record MappingSaveRequest(
        UUID ownerId,
        long baseRevision,
        DatasetKind datasetKind,
        UUID datasetId,
        MappingSnapshot mapping
) {
}
