package com.ontoweave.data.ontology.query.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.ontoweave.data.ontology.common.MappingSnapshot;
import com.ontoweave.data.ontology.query.api.dto.OntologyPropertyResponse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ObjectProjection {

    private ObjectProjection() {
    }

    public static List<OntologyPropertyResponse> project(
            MappingSnapshot mapping,
            JsonNode properties,
            List<UUID> requested
    ) {
        Set<UUID> allowed = attributes(mapping);
        Set<UUID> selected = new LinkedHashSet<>();
        if (requested == null || requested.isEmpty()) {
            addIfPresent(selected, mapping.primaryKeyAttributeId());
            addIfPresent(selected, mapping.titleKeyAttributeId());
        } else {
            for (UUID attributeId : requested) {
                if (!allowed.contains(attributeId)) {
                    throw QueryErrors.invalidMapping(
                            "project 属性不在当前映射中",
                            java.util.Map.of("attributeId", attributeId.toString())
                    );
                }
                selected.add(attributeId);
            }
        }
        return mapping.attributes().stream()
                .filter(attribute -> selected.contains(attribute.attributeId()))
                .map(attribute -> new OntologyPropertyResponse(
                        attribute.attributeId(),
                        MappingSupport.propertyValue(properties, attribute)
                ))
                .toList();
    }

    private static Set<UUID> attributes(MappingSnapshot mapping) {
        return mapping.attributes().stream()
                .map(MappingSnapshot.AttributeMapping::attributeId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static void addIfPresent(Set<UUID> target, UUID value) {
        if (value != null) {
            target.add(value);
        }
    }
}
