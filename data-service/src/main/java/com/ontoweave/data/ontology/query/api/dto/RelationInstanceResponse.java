package com.ontoweave.data.ontology.query.api.dto;

import java.util.UUID;

public record RelationInstanceResponse(
        UUID fromObjectTypeId,
        String fromPrimaryKey,
        UUID toObjectTypeId,
        String toPrimaryKey,
        UUID indexVersionId
) {
}
