package com.ontoweave.data.ontology.query.api.dto;

import java.util.List;
import java.util.UUID;

public record QueryObjectResponse(
        UUID objectTypeId,
        String primaryKey,
        String title,
        List<OntologyPropertyResponse> properties
) {

    public QueryObjectResponse {
        properties = properties == null ? List.of() : List.copyOf(properties);
    }
}
