package com.ontoweave.data.ontology.query.repository;

import java.util.UUID;

public record InstanceRow(
        UUID fromObjectTypeId,
        String fromPrimaryKey,
        UUID toObjectTypeId,
        String toPrimaryKey
) {
}
