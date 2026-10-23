package com.ontoweave.data.ontology.query.api.dto;

import java.util.UUID;

public record OntologyObjectResponse(
        String primaryKey,
        String title,
        UUID indexVersionId
) {
}
