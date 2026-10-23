package com.ontoweave.data.ontology.binding.api.dto;

import java.util.UUID;

public record ObjectBindingSummary(
        UUID objectTypeId,
        boolean bound,
        String status,
        UUID currentIndexVersionId,
        UUID activeDatasetId
) {
}
