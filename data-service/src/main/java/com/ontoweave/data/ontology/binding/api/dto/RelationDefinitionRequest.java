package com.ontoweave.data.ontology.binding.api.dto;

import java.util.UUID;

public record RelationDefinitionRequest(
        UUID ownerId,
        UUID sourceTypeId,
        UUID targetTypeId,
        String name,
        String cardinality,
        UUID foreignKeyPropertyId,
        String foreignKeyDatatype,
        UUID sourcePrimaryKeyAttributeId,
        String sourcePrimaryKeyDatatype,
        UUID targetPrimaryKeyAttributeId,
        String targetPrimaryKeyDatatype,
        long definitionRevision
) {
}
