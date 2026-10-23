package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.DatasetKind;

import java.util.UUID;

public record BindableDatasetResponse(
        DatasetKind kind,
        UUID id,
        String name,
        UUID versionId,
        int versionNumber,
        long rowCount
) {
}
