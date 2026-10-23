package com.ontoweave.data.ontology.query.repository;

import java.util.UUID;

public record RelationVersionRow(
        UUID id,
        UUID relationId,
        String relationKind,
        UUID fromObjectTypeId,
        UUID toObjectTypeId,
        UUID fromObjectVersionId,
        UUID toObjectVersionId
) {
}
