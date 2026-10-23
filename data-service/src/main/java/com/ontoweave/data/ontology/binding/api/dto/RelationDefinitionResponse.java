package com.ontoweave.data.ontology.binding.api.dto;

import com.ontoweave.data.ontology.mapping.MappingValidationResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RelationDefinitionResponse(
        UUID relationId,
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
        long definitionRevision,
        Instant syncedAt,
        List<MappingValidationResult.MappingIssue> warnings
) {

    public RelationDefinitionResponse {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
