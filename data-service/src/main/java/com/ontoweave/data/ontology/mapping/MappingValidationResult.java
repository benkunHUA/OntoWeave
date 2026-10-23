package com.ontoweave.data.ontology.mapping;

import java.util.List;
import java.util.UUID;

public record MappingValidationResult(
        List<MappingIssue> blocking,
        List<MappingIssue> warnings
) {

    public MappingValidationResult {
        blocking = blocking == null ? List.of() : List.copyOf(blocking);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean isValid() {
        return blocking.isEmpty();
    }

    public record MappingIssue(
            String code,
            String message,
            UUID attributeId,
            String column
    ) {
    }
}
