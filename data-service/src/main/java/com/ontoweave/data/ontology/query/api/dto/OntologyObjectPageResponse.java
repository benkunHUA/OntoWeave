package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record OntologyObjectPageResponse(
        List<OntologyObjectResponse> items,
        long total,
        int page,
        int pageSize,
        UUID indexVersionId
) {

    public OntologyObjectPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
