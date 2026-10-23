package com.ontoweave.data.ontology.binding.validation;

import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.mapping.OntologyAttributeDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class BindingMappingDefinitions {

    private BindingMappingDefinitions() {
    }

    static List<OntologyAttributeDefinition> from(MappingSnapshot mapping) {
        if (mapping == null) {
            return List.of();
        }
        List<OntologyAttributeDefinition> definitions = new ArrayList<>();
        Set<UUID> mappedIds = new HashSet<>();
        for (MappingSnapshot.AttributeMapping attribute : mapping.attributes()) {
            definitions.add(new OntologyAttributeDefinition(
                    attribute.attributeId(),
                    null,
                    attribute.datatype(),
                    attribute.required(),
                    attribute.isArray(),
                    attribute.enumValues(),
                    null
            ));
            if (attribute.attributeId() != null) {
                mappedIds.add(attribute.attributeId());
            }
        }
        for (UUID requiredAttributeId : mapping.requiredAttributeIds()) {
            if (requiredAttributeId != null && mappedIds.add(requiredAttributeId)) {
                definitions.add(new OntologyAttributeDefinition(
                        requiredAttributeId,
                        null,
                        null,
                        true,
                        false,
                        null,
                        null
                ));
            }
        }
        return List.copyOf(definitions);
    }
}
