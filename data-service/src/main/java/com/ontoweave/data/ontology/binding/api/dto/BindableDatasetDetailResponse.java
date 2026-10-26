package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.DatasetKind;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BindableDatasetDetailResponse(
        DatasetKind kind,
        UUID id,
        String name,
        UUID versionId,
        int versionNumber,
        long rowCount,
        UUID ownerId,
        Instant publishedAt,
        List<DatasetColumnResponse> columns
) {

    public BindableDatasetDetailResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}
