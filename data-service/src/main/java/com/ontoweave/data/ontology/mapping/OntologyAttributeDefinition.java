package com.ontoweave.data.ontology.mapping;

import java.util.List;
import java.util.UUID;

public record OntologyAttributeDefinition(
        UUID attributeId,
        String name,
        String datatype,
        boolean required,
        boolean isArray,
        List<String> enumValues,
        Integer scale
) {

    public OntologyAttributeDefinition {
        enumValues = enumValues == null ? null : List.copyOf(enumValues);
    }
}
