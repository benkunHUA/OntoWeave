package com.ontoweave.data.ontology.binding.validation;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.mapping.OntologyAttributeDefinition;

import java.util.List;

final class BindingMappingDefinitions {

    private BindingMappingDefinitions() {
    }

    static List<OntologyAttributeDefinition> from(MappingSnapshot mapping) {
        if (mapping == null) {
            return List.of();
        }
        return mapping.attributes().stream()
                .map(attribute -> new OntologyAttributeDefinition(
                        attribute.attributeId(),
                        null,
                        attribute.datatype(),
                        attribute.required(),
                        attribute.isArray(),
                        attribute.enumValues(),
                        null
                ))
                .toList();
    }
}
