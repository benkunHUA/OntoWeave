package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.common.DatasetKind;
import com.ontoweave.data.ontology.common.MappingSnapshot;

import java.util.UUID;

public record PreviewBindingRequest(
        UUID ownerId,
        DatasetKind datasetKind,
        UUID datasetId,
        MappingSnapshot mapping,
        Integer limit
) {
}
