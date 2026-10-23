package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record OntologyObjectDetailResponse(
        String primaryKey,
        String title,
        UUID indexVersionId,
        List<OntologyPropertyResponse> properties
) {

    public OntologyObjectDetailResponse {
        properties = properties == null ? List.of() : List.copyOf(properties);
    }
}
